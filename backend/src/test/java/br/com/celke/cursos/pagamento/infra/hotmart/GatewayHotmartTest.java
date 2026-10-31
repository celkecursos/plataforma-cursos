package br.com.celke.cursos.pagamento.infra.hotmart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.matricula.domain.FormaPagamento;
import br.com.celke.cursos.pagamento.domain.Checkout;
import br.com.celke.cursos.pagamento.domain.Comprador;
import br.com.celke.cursos.pagamento.domain.ConfiguracaoGateway;
import br.com.celke.cursos.pagamento.domain.EventoPagamento;
import br.com.celke.cursos.pagamento.domain.NotificacaoRecebida;
import br.com.celke.cursos.pagamento.domain.Oferta;
import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.pagamento.domain.TipoGateway;
import br.com.celke.cursos.pagamento.infra.hotmart.ClienteApiHotmart.Credenciais;
import br.com.celke.cursos.pagamento.infra.hotmart.ClienteApiHotmart.FalhaApiHotmart;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class GatewayHotmartTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final String HOTTOK = "hottok-de-teste-123";

    @Mock
    private ClienteApiHotmart api;

    private final JsonMapper json = JsonMapper.builder().build();
    private GatewayHotmart gateway;

    @BeforeEach
    void setUp() {
        gateway = new GatewayHotmart(new InterpretadorHotmart(json), api, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static ConfiguracaoGateway config(String... chavesEValores) {
        Map<String, String> valores = new HashMap<>();
        valores.put("hottok", HOTTOK);
        for (int i = 0; i < chavesEValores.length; i += 2) {
            valores.put(chavesEValores[i], chavesEValores[i + 1]);
        }
        return new ConfiguracaoGateway(1L, TipoGateway.HOTMART, valores);
    }

    private static NotificacaoRecebida notificacao(Map<String, String> cabecalhos) {
        return new NotificacaoRecebida(cabecalhos, "{}".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("declara os campos do painel e o que sabe fazer")
    void descricao() {
        assertThat(gateway.tipo()).isEqualTo(TipoGateway.HOTMART);
        assertThat(gateway.nome()).isEqualTo("Hotmart");
        assertThat(gateway.camposConfiguracao())
                .extracting(c -> c.chave() + (c.secreto() ? "*" : ""))
                .containsExactly("hottok*", "ambiente", "urlCheckoutBase", "clientId", "clientSecret*", "basic*");
        assertThat(gateway.capacidades()).hasSize(3);
    }

    @Nested
    class Autenticar {

        @Test
        @DisplayName("hottok certo no cabeçalho, com qualquer caixa no nome")
        void hottokCerto() {
            assertThat(gateway.autenticar(notificacao(Map.of("x-hotmart-hottok", HOTTOK)), config()))
                    .isTrue();
            assertThat(gateway.autenticar(notificacao(Map.of("X-HOTMART-HOTTOK", " " + HOTTOK + " ")), config()))
                    .isTrue();
        }

        @Test
        @DisplayName("hottok errado, parecido, ausente ou não configurado é recusado")
        void hottokErrado() {
            assertThat(gateway.autenticar(notificacao(Map.of("X-HOTMART-HOTTOK", "outro")), config()))
                    .isFalse();
            assertThat(gateway.autenticar(notificacao(Map.of("X-HOTMART-HOTTOK", HOTTOK + "4")), config()))
                    .isFalse();
            assertThat(gateway.autenticar(notificacao(Map.of()), config())).isFalse();
            assertThat(gateway.autenticar(
                            notificacao(Map.of("X-HOTMART-HOTTOK", HOTTOK)),
                            new ConfiguracaoGateway(1L, TipoGateway.HOTMART, Map.of())))
                    .isFalse();
        }
    }

    @Nested
    class IniciarCheckout {

        private Oferta oferta(String codigoCheckout) {
            Oferta oferta = new Oferta(
                    5L,
                    1L,
                    new Oferta.DadosOferta("4774438", "k2 pasun0", codigoCheckout, "À vista", 19_700, "BRL", null),
                    AGORA);
            ReflectionTestUtils.setField(oferta, "id", 9L);
            return oferta;
        }

        @Test
        @DisplayName("redireciona para o checkout com oferta, e-mail, nome e o código do pedido codificados")
        void url() {
            Oferta oferta = oferta("X12345678Y");
            Pedido pedido = Pedido.doCheckout(oferta, 7L, "ana+teste@celke.uno", "Ana Maria", AGORA);

            Checkout checkout = gateway.iniciarCheckout(
                    pedido, oferta, new Comprador("ana+teste@celke.uno", "Ana & Maria"), config());

            assertThat(checkout)
                    .isEqualTo(new Checkout.Redirecionamento("https://pay.hotmart.com/X12345678Y?off=k2+pasun0"
                            + "&email=ana%2Bteste%40celke.uno&name=Ana+%26+Maria&xcod=" + pedido.getCodigo()
                            + "&sck=plataforma"));
        }

        @Test
        @DisplayName("sem código de checkout usa o id do produto; endereço configurado sem a barra final; sem nome")
        void enderecoConfigurado() {
            Oferta oferta = oferta(null);
            Pedido pedido = Pedido.doCheckout(oferta, 7L, "ana@celke.uno", null, AGORA);

            Checkout checkout = gateway.iniciarCheckout(
                    pedido,
                    oferta,
                    new Comprador("ana@celke.uno", null),
                    config("urlCheckoutBase", "https://sandbox.hotmart.com/"));

            assertThat(((Checkout.Redirecionamento) checkout).url())
                    .startsWith("https://sandbox.hotmart.com/4774438?off=")
                    .doesNotContain("&name=");
        }
    }

    @Nested
    class Testar {

        @Test
        @DisplayName("só o hottok: ok, avisando que a reconciliação fica desligada")
        void soHottok() {
            var resultado = gateway.testar(config());

            assertThat(resultado.ok()).isTrue();
            assertThat(resultado.mensagem()).startsWith("Hottok gravado.");
            verify(api, never()).token(any());
        }

        @Test
        @DisplayName("sem hottok ou com credenciais da API pela metade: falha")
        void incompleto() {
            assertThat(gateway.testar(new ConfiguracaoGateway(1L, TipoGateway.HOTMART, Map.of())))
                    .isEqualTo(new br.com.celke.cursos.pagamento.domain.ResultadoTeste(false, "Informe o hottok."));
            assertThat(gateway.testar(config("clientId", "id")).mensagem())
                    .isEqualTo("Informe o Client ID, o Client Secret e o Basic juntos.");
        }

        @Test
        @DisplayName("credenciais aceitas pela Hotmart")
        void aceitas() {
            when(api.token(new Credenciais("id", "segredo", "YmFzaWM=", true))).thenReturn("token");

            var resultado = gateway.testar(config(
                    "clientId", "id", "clientSecret", "segredo", "basic", "Basic YmFzaWM=", "ambiente", "SANDBOX"));

            assertThat(resultado.ok()).isTrue();
            assertThat(resultado.mensagem()).isEqualTo("Credenciais da API conferidas com a Hotmart.");
        }

        @Test
        @DisplayName("credenciais recusadas: falha com o motivo")
        void recusadas() {
            when(api.token(any())).thenThrow(new FalhaApiHotmart("A Hotmart recusou a chamada (HTTP 401).", 401));

            var resultado = gateway.testar(config("clientId", "id", "clientSecret", "x", "basic", "y"));

            assertThat(resultado.ok()).isFalse();
            assertThat(resultado.mensagem())
                    .isEqualTo(
                            "A Hotmart recusou a chamada (HTTP 401). Confira o Client ID, o Client Secret e o Basic.");
        }
    }

    @Nested
    class Consultar {

        private final ConfiguracaoGateway comApi = config("clientId", "id", "clientSecret", "x", "basic", "y");

        @Test
        @DisplayName("sem credenciais da API não consulta")
        void semCredenciais() {
            assertThat(gateway.consultar("HP1", config())).isEmpty();
            verify(api, never()).venda(any(), any(), any());
        }

        @Test
        @DisplayName("venda aprovada vira evento sintético com o status interno")
        void aprovada() {
            when(api.venda(any(), eq("HP1"), isNull())).thenReturn(Optional.of(json.readTree("""
                    {"product":{"id":4774438},"buyer":{"email":"ANA@celke.uno","name":"Ana"},
                     "purchase":{"transaction":"HP1","status":"APPROVED","offer":{"code":"k2"},
                       "price":{"value":197.0,"currency_code":"BRL"},"payment":{"type":"PIX"},
                       "tracking":{"external_code":"COD"}}}
                    """)));

            EventoPagamento evento = gateway.consultar("HP1", comApi).orElseThrow();

            assertThat(evento)
                    .isEqualTo(new EventoPagamento(
                            "reconciliacao:HP1:APPROVED",
                            "RECONCILIACAO_APPROVED",
                            AGORA,
                            "HP1",
                            StatusPedido.APROVADO,
                            "COD",
                            "4774438",
                            "k2",
                            new EventoPagamento.CompradorEvento("ana@celke.uno", "Ana", null),
                            19_700L,
                            "BRL",
                            FormaPagamento.PIX,
                            null));
        }

        @Test
        @DisplayName("fora das aprovadas, procura status a status até achar")
        void reembolsada() {
            when(api.venda(any(), eq("HP1"), isNull())).thenReturn(Optional.empty());
            when(api.venda(any(), eq("HP1"), eq("REFUNDED"))).thenReturn(Optional.of(json.readTree("""
                    {"purchase":{"status":"REFUNDED"}}
                    """)));

            EventoPagamento evento = gateway.consultar("HP1", comApi).orElseThrow();

            assertThat(evento.statusPedido()).isEqualTo(StatusPedido.REEMBOLSADO);
            assertThat(evento.comprador()).isNull();
        }

        @Test
        @DisplayName("transação que não aparece em nenhum status: vazio")
        void naoEncontrada() {
            when(api.venda(any(), eq("HP1"), any())).thenReturn(Optional.empty());

            assertThat(gateway.consultar("HP1", comApi)).isEmpty();
            verify(api).venda(any(), eq("HP1"), eq("PRINTED_BILLET"));
        }
    }

    @Test
    @DisplayName("interpreta a notificação pelo corpo")
    void interpretar() {
        var eventos = gateway.interpretar(new NotificacaoRecebida(
                Map.of(), FixturesHotmart.corpo("PURCHASE_REFUNDED").getBytes(StandardCharsets.UTF_8)));

        assertThat(eventos)
                .singleElement()
                .extracting(EventoPagamento::statusPedido)
                .isEqualTo(StatusPedido.REEMBOLSADO);
    }
}
