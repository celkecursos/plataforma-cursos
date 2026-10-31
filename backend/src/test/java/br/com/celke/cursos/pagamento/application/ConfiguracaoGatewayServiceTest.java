package br.com.celke.cursos.pagamento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.pagamento.application.ConfiguracaoGatewayService.SegredoResumo;
import br.com.celke.cursos.pagamento.domain.Gateway;
import br.com.celke.cursos.pagamento.domain.TipoGateway;
import br.com.celke.cursos.pagamento.infra.hotmart.GatewayHotmart;
import br.com.celke.cursos.pagamento.infra.hotmart.InterpretadorHotmart;
import br.com.celke.cursos.shared.cripto.CifraSegredos;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.ErroCampo;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ConfiguracaoGatewayServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final String CHAVE = "dGVzdGUtZGEtY2hhdmUtZGUtcGFnYW1lbnRvcy0zMmI=";

    private final JsonMapper json = JsonMapper.builder().build();
    private final GatewaysRegistrados registrados = new GatewaysRegistrados(
            List.of(new GatewayHotmart(new InterpretadorHotmart(json), null, Clock.fixed(AGORA, ZoneOffset.UTC))));
    private final ConfiguracaoGatewayService service =
            new ConfiguracaoGatewayService(registrados, new CifraSegredos(CHAVE, "PAGAMENTOS_CHAVE"), json);

    private static Gateway gateway() {
        return new Gateway(TipoGateway.HOTMART, "Hotmart", "token-do-webhook", AGORA);
    }

    private static Map<String, String> valores(String... chavesEValores) {
        Map<String, String> valores = new HashMap<>();
        for (int i = 0; i < chavesEValores.length; i += 2) {
            valores.put(chavesEValores[i], chavesEValores[i + 1]);
        }
        return valores;
    }

    @Test
    @DisplayName("separa os segredos (cifrados) dos campos comuns e aplica os padrões")
    void grava() {
        Gateway gateway = gateway();

        service.configurar(gateway, " Minha Hotmart ", valores("hottok", " segredo-hottok ", "clientId", "abc"), AGORA);

        assertThat(gateway.getNome()).isEqualTo("Minha Hotmart");
        assertThat(gateway.getConfiguracao())
                .isEqualTo(
                        "{\"ambiente\":\"PRODUCAO\",\"clientId\":\"abc\",\"urlCheckoutBase\":\"https://pay.hotmart.com\"}");
        assertThat(gateway.getSegredos()).isNotNull();
        assertThat(service.segredos(gateway)).isEqualTo(Map.of("hottok", "segredo-hottok"));
        assertThat(service.ler(gateway).valores())
                .containsEntry("hottok", "segredo-hottok")
                .containsEntry("clientId", "abc")
                .containsEntry("ambiente", "PRODUCAO");
    }

    @Test
    @DisplayName("segredo em branco mantém o valor atual; informado, troca")
    void segredoEmBrancoMantem() {
        Gateway gateway = gateway();
        service.configurar(gateway, "Hotmart", valores("hottok", "antigo", "basic", "b-antigo"), AGORA);

        service.configurar(gateway, "Hotmart", valores("hottok", "", "basic", "b-novo"), AGORA);

        assertThat(service.segredos(gateway)).isEqualTo(Map.of("hottok", "antigo", "basic", "b-novo"));
        assertThat(service.resumoSegredos(gateway))
                .containsExactly(
                        Map.entry("hottok", new SegredoResumo(true, "tigo")),
                        Map.entry("clientSecret", new SegredoResumo(false, null)),
                        Map.entry("basic", new SegredoResumo(true, "novo")));
    }

    @Test
    @DisplayName("segredo curto demais não mostra os últimos caracteres")
    void segredoCurto() {
        Gateway gateway = gateway();
        service.configurar(gateway, "Hotmart", valores("hottok", "1234"), AGORA);

        assertThat(service.resumoSegredos(gateway).get("hottok")).isEqualTo(new SegredoResumo(true, null));
    }

    @Test
    @DisplayName("campo obrigatório, opção, endereço, tamanho e campo desconhecido viram erros de campo")
    void validacao() {
        Gateway gateway = gateway();

        assertThatThrownBy(() -> service.configurar(
                        gateway,
                        "Hotmart",
                        valores(
                                "ambiente", "TESTE",
                                "urlCheckoutBase", "ftp://pay",
                                "clientId", "x".repeat(501),
                                "inventado", "1"),
                        AGORA))
                .isInstanceOf(ConfiguracaoInvalidaException.class)
                .hasMessage("Dados inválidos.")
                .extracting(e -> ((ConfiguracaoInvalidaException) e).getErros())
                .isEqualTo(List.of(
                        new ErroCampo("valores.ambiente", "Escolha uma das opções: PRODUCAO, SANDBOX."),
                        new ErroCampo("valores.clientId", "Use no máximo 500 caracteres."),
                        new ErroCampo("valores.hottok", "Informe Hottok."),
                        new ErroCampo("valores.inventado", "Campo desconhecido."),
                        new ErroCampo("valores.urlCheckoutBase", "Informe um endereço começando com https://.")));
        assertThat(gateway.getSegredos()).isNull();
        assertThat(gateway.getConfiguracao()).isEqualTo("{}");
    }

    @Test
    @DisplayName("endereço sem host ou malformado é recusado; valores nulos contam como vazios")
    void enderecoInvalido() {
        assertThatThrownBy(() -> service.configurar(
                        gateway(), "Hotmart", valores("hottok", "h", "urlCheckoutBase", "https://"), AGORA))
                .isInstanceOf(ConfiguracaoInvalidaException.class);
        assertThatThrownBy(() -> service.configurar(
                        gateway(), "Hotmart", valores("hottok", "h", "urlCheckoutBase", "https://a b"), AGORA))
                .isInstanceOf(ConfiguracaoInvalidaException.class);
        assertThatThrownBy(() -> service.configurar(gateway(), "Hotmart", null, AGORA))
                .isInstanceOf(ConfiguracaoInvalidaException.class);
    }

    @Test
    @DisplayName("sem a PAGAMENTOS_CHAVE não grava segredos")
    void semChave() {
        var semChave = new ConfiguracaoGatewayService(registrados, new CifraSegredos("", "PAGAMENTOS_CHAVE"), json);
        Gateway gateway = gateway();

        assertThatThrownBy(() -> semChave.configurar(gateway, "Hotmart", valores("hottok", "h"), AGORA))
                .isInstanceOf(ConflitoException.class)
                .hasMessage(
                        "Defina a PAGAMENTOS_CHAVE no .env do ambiente para gravar as credenciais do meio de pagamento.");
        assertThat(gateway.getSegredos()).isNull();
    }

    @Test
    @DisplayName("sem segredos gravados, a leitura traz só os comuns")
    void semSegredos() {
        assertThat(service.ler(gateway()).valores()).isEmpty();
        assertThat(service.segredos(gateway())).isEmpty();
    }
}
