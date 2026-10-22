package br.com.celke.cursos.pagamento.infra.hotmart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.celke.cursos.pagamento.infra.hotmart.ClienteApiHotmart.Credenciais;
import br.com.celke.cursos.pagamento.infra.hotmart.ClienteApiHotmart.FalhaApiHotmart;
import br.com.celke.cursos.support.fixtures.RelogioAjustavel;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ClienteApiHotmartTest {

    private static final String TOKEN_URL = "https://token.hotmart.test/security/oauth/token";
    private static final HotmartProperties PROPS = new HotmartProperties(
            TOKEN_URL, "https://api.hotmart.test/v1", "https://sandbox.hotmart.test/v1", Duration.ofSeconds(5));
    private static final Credenciais CREDENCIAIS = new Credenciais("id", "segredo", "Basic YmFzaWM=", false);

    private final RelogioAjustavel relogio = new RelogioAjustavel(Instant.parse("2026-03-10T12:00:00Z"));
    private MockRestServiceServer servidor;
    private ClienteApiHotmart cliente;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new ClienteApiHotmart(builder.build(), PROPS, relogio);
    }

    private void esperarToken(int expiraEm) {
        servidor.expect(
                        once(),
                        requestTo(TOKEN_URL + "?grant_type=client_credentials&client_id=id&client_secret=segredo"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic YmFzaWM="))
                .andRespond(withSuccess(
                        "{\"access_token\":\"tok-%d\",\"expires_in\":%d}".formatted(expiraEm, expiraEm),
                        MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("pede o token com o Basic (sem repetir o prefixo) e guarda até 5 minutos antes de vencer")
    void tokenEmCache() {
        esperarToken(3600);

        assertThat(cliente.token(CREDENCIAIS)).isEqualTo("tok-3600");
        relogio.avancar(Duration.ofMinutes(54).plusSeconds(59));
        assertThat(cliente.token(CREDENCIAIS)).isEqualTo("tok-3600");
        servidor.verify();

        servidor.reset();
        esperarToken(1800);
        relogio.avancar(Duration.ofSeconds(1));
        assertThat(cliente.token(CREDENCIAIS)).isEqualTo("tok-1800");
        servidor.verify();
    }

    @Test
    @DisplayName("credencial recusada vira falha com o status")
    void recusada() {
        servidor.expect(requestTo(org.hamcrest.Matchers.startsWith(TOKEN_URL)))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> cliente.token(CREDENCIAIS))
                .isInstanceOf(FalhaApiHotmart.class)
                .hasMessage("A Hotmart recusou a chamada (HTTP 401).")
                .extracting(e -> ((FalhaApiHotmart) e).getStatus())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("resposta sem token é falha")
    void semToken() {
        servidor.expect(requestTo(org.hamcrest.Matchers.startsWith(TOKEN_URL)))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.token(CREDENCIAIS))
                .isInstanceOf(FalhaApiHotmart.class)
                .hasMessage("A Hotmart não devolveu o token de acesso.");
    }

    @Test
    @DisplayName("histórico de vendas pela transação, com e sem filtro de status, e no sandbox")
    void venda() {
        esperarToken(3600);
        servidor.expect(requestTo("https://api.hotmart.test/v1/sales/history?transaction=HP1"))
                .andExpect(header("Authorization", "Bearer tok-3600"))
                .andRespond(withSuccess(
                        "{\"items\":[{\"purchase\":{\"status\":\"APPROVED\"}}]}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(
                        "https://api.hotmart.test/v1/sales/history?transaction=HP1&transaction_status=REFUNDED"))
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));

        assertThat(cliente.venda(CREDENCIAIS, "HP1", null))
                .hasValueSatisfying(
                        item -> assertThat(item.path("purchase").path("status").asString())
                                .isEqualTo("APPROVED"));
        assertThat(cliente.venda(CREDENCIAIS, "HP1", "REFUNDED")).isEmpty();
        servidor.verify();
    }

    @Test
    @DisplayName("no sandbox usa o endereço do sandbox")
    void sandbox() {
        Credenciais sandbox = new Credenciais("id", "segredo", "YmFzaWM=", true);
        esperarToken(3600);
        servidor.expect(requestTo("https://sandbox.hotmart.test/v1/sales/history?transaction=HP1"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(cliente.venda(sandbox, "HP1", null)).isEmpty();
        servidor.verify();
    }

    @Test
    @DisplayName("o cliente HTTP de produção usa o tempo máximo configurado")
    void restClient() {
        assertThat(ClienteApiHotmart.restClient(RestClient.builder(), PROPS)).isNotNull();
    }
}
