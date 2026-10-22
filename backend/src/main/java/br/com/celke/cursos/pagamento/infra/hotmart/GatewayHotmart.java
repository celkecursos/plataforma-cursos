package br.com.celke.cursos.pagamento.infra.hotmart;

import br.com.celke.cursos.pagamento.domain.CampoConfiguracao;
import br.com.celke.cursos.pagamento.domain.Capacidade;
import br.com.celke.cursos.pagamento.domain.Checkout;
import br.com.celke.cursos.pagamento.domain.Comprador;
import br.com.celke.cursos.pagamento.domain.ConfiguracaoGateway;
import br.com.celke.cursos.pagamento.domain.EventoPagamento;
import br.com.celke.cursos.pagamento.domain.EventoPagamento.CompradorEvento;
import br.com.celke.cursos.pagamento.domain.GatewayPagamento;
import br.com.celke.cursos.pagamento.domain.NotificacaoRecebida;
import br.com.celke.cursos.pagamento.domain.Oferta;
import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.ResultadoTeste;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.pagamento.domain.TipoCampo;
import br.com.celke.cursos.pagamento.domain.TipoGateway;
import br.com.celke.cursos.pagamento.infra.hotmart.ClienteApiHotmart.Credenciais;
import br.com.celke.cursos.pagamento.infra.hotmart.ClienteApiHotmart.FalhaApiHotmart;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;

/**
 * Hotmart com o webhook 2.0.0: notificação autenticada pelo hottok no cabeçalho
 * {@code X-HOTMART-HOTTOK} (a Hotmart não assina o corpo), checkout por redirecionamento para o
 * {@code pay.hotmart.com} com o código do pedido no {@code xcod}, e consulta das vendas pela API.
 */
public class GatewayHotmart implements GatewayPagamento {

    public static final String CABECALHO_HOTTOK = "X-HOTMART-HOTTOK";
    static final String CHECKOUT_PADRAO = "https://pay.hotmart.com";

    /** Status consultados um a um quando a venda não está entre as aprovadas/completas. */
    static final List<String> STATUS_CONSULTADOS = List.of(
            "REFUNDED",
            "CHARGEBACK",
            "CANCELLED",
            "EXPIRED",
            "DISPUTE",
            "OVERDUE",
            "WAITING_PAYMENT",
            "PRINTED_BILLET");

    private static final Logger LOG = LoggerFactory.getLogger(GatewayHotmart.class);

    private static final List<CampoConfiguracao> CAMPOS = List.of(
            CampoConfiguracao.segredo(
                    "hottok",
                    "Hottok",
                    true,
                    "Ferramentas → Webhook na Hotmart: o token que vai no cabeçalho de cada notificação."),
            new CampoConfiguracao(
                    "ambiente",
                    "Ambiente",
                    TipoCampo.SELECAO,
                    false,
                    true,
                    "Sandbox para testar com a conta de testes da Hotmart.",
                    "PRODUCAO",
                    List.of("PRODUCAO", "SANDBOX")),
            new CampoConfiguracao(
                    "urlCheckoutBase",
                    "Endereço do checkout",
                    TipoCampo.URL,
                    false,
                    true,
                    "Início do link de pagamento das ofertas.",
                    CHECKOUT_PADRAO,
                    List.of()),
            CampoConfiguracao.texto(
                    "clientId", "Client ID", false, "Ferramentas → Credenciais (API). Só para a reconciliação."),
            CampoConfiguracao.segredo("clientSecret", "Client Secret", false, "Ferramentas → Credenciais (API)."),
            CampoConfiguracao.segredo("basic", "Basic", false, "Ferramentas → Credenciais (API): o token Basic."));

    private final InterpretadorHotmart interpretador;
    private final ClienteApiHotmart api;
    private final Clock clock;

    public GatewayHotmart(InterpretadorHotmart interpretador, ClienteApiHotmart api, Clock clock) {
        this.interpretador = interpretador;
        this.api = api;
        this.clock = clock;
    }

    @Override
    public TipoGateway tipo() {
        return TipoGateway.HOTMART;
    }

    @Override
    public String nome() {
        return "Hotmart";
    }

    @Override
    public List<CampoConfiguracao> camposConfiguracao() {
        return CAMPOS;
    }

    @Override
    public Set<Capacidade> capacidades() {
        return EnumSet.of(Capacidade.CHECKOUT_REDIRECIONAMENTO, Capacidade.CONSULTA_API, Capacidade.RECORRENCIA);
    }

    @Override
    public ResultadoTeste testar(ConfiguracaoGateway configuracao) {
        if (configuracao.valor("hottok") == null) {
            return ResultadoTeste.falha("Informe o hottok.");
        }
        Optional<Credenciais> credenciais;
        try {
            credenciais = credenciais(configuracao);
        } catch (IllegalArgumentException e) {
            return ResultadoTeste.falha(e.getMessage());
        }
        if (credenciais.isEmpty()) {
            return ResultadoTeste.ok("Hottok gravado. Sem as credenciais da API, a reconciliação fica desligada"
                    + " para este meio de pagamento.");
        }
        try {
            api.token(credenciais.get());
            return ResultadoTeste.ok("Credenciais da API conferidas com a Hotmart.");
        } catch (FalhaApiHotmart e) {
            return ResultadoTeste.falha(e.getMessage() + " Confira o Client ID, o Client Secret e o Basic.");
        }
    }

    /**
     * {@code <checkout>/<produto>?off=<oferta>&email=&name=&xcod=<pedido>&sck=plataforma}: o e-mail e o
     * nome chegam preenchidos e o {@code xcod} volta na notificação.
     */
    @Override
    public Checkout iniciarCheckout(
            Pedido pedido, Oferta oferta, Comprador comprador, ConfiguracaoGateway configuracao) {
        String base = configuracao.valor("urlCheckoutBase", CHECKOUT_PADRAO).replaceAll("/+$", "");
        StringBuilder url = new StringBuilder(base)
                .append('/')
                .append(UriUtils.encodePathSegment(oferta.codigoParaCheckout(), StandardCharsets.UTF_8))
                .append("?off=")
                .append(codificar(oferta.getOfertaExterna()))
                .append("&email=")
                .append(codificar(comprador.email()));
        if (comprador.nome() != null && !comprador.nome().isBlank()) {
            url.append("&name=").append(codificar(comprador.nome()));
        }
        url.append("&xcod=").append(codificar(pedido.getCodigo())).append("&sck=plataforma");
        return new Checkout.Redirecionamento(url.toString());
    }

    /** Hottok do cabeçalho comparado em tempo constante com o configurado. */
    @Override
    public boolean autenticar(NotificacaoRecebida notificacao, ConfiguracaoGateway configuracao) {
        String esperado = configuracao.valor("hottok");
        String recebido = notificacao.cabecalho(CABECALHO_HOTTOK);
        if (esperado == null || recebido == null) {
            return false;
        }
        return MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8), recebido.strip().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public List<EventoPagamento> interpretar(NotificacaoRecebida notificacao) {
        return List.of(interpretador.interpretar(notificacao.corpoTexto()));
    }

    /** A venda no histórico de vendas, como evento sintético ({@code reconciliacao:<transação>:<status>}). */
    @Override
    public Optional<EventoPagamento> consultar(String transacao, ConfiguracaoGateway configuracao) {
        Optional<Credenciais> credenciais = credenciais(configuracao);
        if (credenciais.isEmpty()) {
            LOG.debug("Hotmart sem credenciais da API: transação {} não consultada", transacao);
            return Optional.empty();
        }
        Optional<JsonNode> venda = api.venda(credenciais.get(), transacao, null);
        for (int i = 0; venda.isEmpty() && i < STATUS_CONSULTADOS.size(); i++) {
            venda = api.venda(credenciais.get(), transacao, STATUS_CONSULTADOS.get(i));
        }
        return venda.map(item -> evento(transacao, item));
    }

    private EventoPagamento evento(String transacao, JsonNode item) {
        JsonNode compra = item.path("purchase");
        String status = Optional.ofNullable(InterpretadorHotmart.texto(compra.path("status")))
                .map(s -> s.toUpperCase(Locale.ROOT))
                .orElse("DESCONHECIDO");
        StatusPedido statusPedido = InterpretadorHotmart.POR_STATUS.get(status);
        JsonNode comprador = item.path("buyer");
        String email = InterpretadorHotmart.texto(comprador.path("email"));
        return new EventoPagamento(
                "reconciliacao:%s:%s".formatted(transacao, status),
                "RECONCILIACAO_" + status,
                clock.instant(),
                transacao,
                statusPedido,
                InterpretadorHotmart.texto(compra.path("tracking").path("external_code")),
                InterpretadorHotmart.texto(item.path("product").path("id")),
                InterpretadorHotmart.texto(compra.path("offer").path("code")),
                email == null
                        ? null
                        : new CompradorEvento(
                                email.toLowerCase(Locale.ROOT),
                                InterpretadorHotmart.texto(comprador.path("name")),
                                null),
                InterpretadorHotmart.centavos(compra.path("price").path("value")),
                InterpretadorHotmart.texto(compra.path("price").path("currency_code")),
                InterpretadorHotmart.forma(
                        InterpretadorHotmart.texto(compra.path("payment").path("type"))),
                null);
    }

    /** Credenciais da API, se as três foram informadas. */
    private static Optional<Credenciais> credenciais(ConfiguracaoGateway configuracao) {
        String clientId = configuracao.valor("clientId");
        String clientSecret = configuracao.valor("clientSecret");
        String basic = configuracao.valor("basic");
        if (clientId == null && clientSecret == null && basic == null) {
            return Optional.empty();
        }
        if (clientId == null || clientSecret == null || basic == null) {
            throw new IllegalArgumentException("Informe o Client ID, o Client Secret e o Basic juntos.");
        }
        return Optional.of(
                new Credenciais(clientId, clientSecret, basic, "SANDBOX".equals(configuracao.valor("ambiente"))));
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
