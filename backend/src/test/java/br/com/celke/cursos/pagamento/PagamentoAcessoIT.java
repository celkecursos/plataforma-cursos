package br.com.celke.cursos.pagamento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.matricula.MatriculaITHelper;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: matriz de papéis das rotas de pagamento — ADM ({@code /api/adm/pagamentos}, ofertas: 401
 * anônimo, 403 ALUNO, ADMIN passa), checkout (privada) e as públicas (situação do pedido e webhook).
 * Não cobre: regras de cada rota (GatewayAdmIT, WebhookIT, PagamentoFluxoIT).
 * Dado criado: curso it-<stamp> publicado, uma Hotmart ativa, uma oferta e um pedido.
 */
class PagamentoAcessoIT extends IntegrationTest {

    private String tokenAdmin;
    private String tokenAluno;
    private PagamentoITHelper.Gateway gateway;
    private long turma;
    private long oferta;
    private String pedido;

    @BeforeEach
    void criarEstrutura() {
        CursoITHelper adm = new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA)));
        PagamentoITHelper pagamentos = new PagamentoITHelper(mvc, adm);
        turma = new MatriculaITHelper(adm).cursoPublicado(stamp()).turma();
        gateway = pagamentos.hotmart("hottok-" + stamp(), true);
        oferta = pagamentos.oferta(turma, stamp(), "oferta", null);
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);
        UsuarioTeste admin = criarUsuario(Papel.ADMIN);
        tokenAluno = login(aluno.email(), aluno.senha());
        tokenAdmin = login(admin.email(), admin.senha());
        pedido = pagamentos.checkout(bearer(tokenAluno), oferta);
    }

    static Stream<Arguments> rotasAdm() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/adm/pagamentos/tipos", null, HttpStatus.OK),
                Arguments.of(HttpMethod.GET, "/api/adm/pagamentos/gateways", null, HttpStatus.OK),
                Arguments.of(HttpMethod.GET, "/api/adm/pagamentos/gateways/{g}", null, HttpStatus.OK),
                Arguments.of(
                        HttpMethod.POST,
                        "/api/adm/pagamentos/gateways",
                        "{\"tipo\":\"HOTMART\",\"nome\":\"Outra\",\"valores\":{\"hottok\":\"h\"}}",
                        HttpStatus.CREATED),
                Arguments.of(
                        HttpMethod.PUT,
                        "/api/adm/pagamentos/gateways/{g}",
                        "{\"nome\":\"Hotmart IT\",\"valores\":{}}",
                        HttpStatus.OK),
                Arguments.of(HttpMethod.POST, "/api/adm/pagamentos/gateways/{g}/teste", null, HttpStatus.OK),
                Arguments.of(HttpMethod.POST, "/api/adm/pagamentos/gateways/{g}/ativacao", null, HttpStatus.OK),
                Arguments.of(HttpMethod.DELETE, "/api/adm/pagamentos/gateways/{g}/ativacao", null, HttpStatus.OK),
                Arguments.of(HttpMethod.GET, "/api/adm/turmas/{t}/ofertas", null, HttpStatus.OK),
                Arguments.of(
                        HttpMethod.POST,
                        "/api/adm/turmas/{t}/ofertas",
                        "{\"gatewayId\":{g},\"produtoExterno\":\"{novo}\",\"ofertaExterna\":\"o\",\"titulo\":\"t\","
                                + "\"precoCentavos\":1,\"moeda\":\"BRL\"}",
                        HttpStatus.CREATED),
                Arguments.of(
                        HttpMethod.PUT,
                        "/api/adm/ofertas/{o}",
                        "{\"produtoExterno\":\"{novo}\",\"ofertaExterna\":\"o\",\"titulo\":\"t\",\"precoCentavos\":1,"
                                + "\"moeda\":\"BRL\"}",
                        HttpStatus.OK),
                Arguments.of(HttpMethod.PATCH, "/api/adm/ofertas/{o}/status", "{\"ativa\":true}", HttpStatus.OK),
                Arguments.of(HttpMethod.GET, "/api/adm/pagamentos/pedidos", null, HttpStatus.OK),
                Arguments.of(HttpMethod.GET, "/api/adm/pagamentos/eventos", null, HttpStatus.OK));
    }

    private MvcTestResult chamar(HttpMethod metodo, String rota, String corpo, String token) {
        String novo = stamp();
        var requisicao = mvc.method(metodo)
                .uri(rota.replace("{g}", String.valueOf(gateway.id()))
                        .replace("{t}", String.valueOf(turma))
                        .replace("{o}", String.valueOf(oferta)));
        if (corpo != null) {
            requisicao = requisicao
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(corpo.replace("{g}", String.valueOf(gateway.id())).replace("{novo}", novo));
        }
        if (token != null) {
            requisicao = requisicao.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return requisicao.exchange();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rotasAdm")
    @DisplayName("ADM: anônimo 401, ALUNO 403")
    void admSemPapel(HttpMethod metodo, String rota, String corpo, HttpStatus admin) {
        assertThat(chamar(metodo, rota, corpo, null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(metodo, rota, corpo, tokenAluno))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Acesso negado.");
    }

    @ParameterizedTest(name = "{0} {1} → {3}")
    @MethodSource("rotasAdm")
    @DisplayName("ADM: ADMIN passa")
    void admAdmin(HttpMethod metodo, String rota, String corpo, HttpStatus admin) {
        assertThat(chamar(metodo, rota, corpo, tokenAdmin)).hasStatus(admin);
    }

    @Test
    @DisplayName("checkout: anônimo 401; ALUNO e ADMIN compram")
    void checkout() {
        String corpo = "{\"ofertaId\":%d}".formatted(oferta);
        assertThat(chamar(HttpMethod.POST, "/api/pagamentos/checkout", corpo, null))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Autenticação necessária.");
        assertThat(chamar(HttpMethod.POST, "/api/pagamentos/checkout", corpo, tokenAluno))
                .hasStatus(HttpStatus.CREATED);
        assertThat(chamar(HttpMethod.POST, "/api/pagamentos/checkout", corpo, tokenAdmin))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("situação do pedido é pública (só GET); o webhook é público, mas exige o hottok")
    void publicas() {
        assertThat(chamar(HttpMethod.GET, "/api/pagamentos/pedidos/" + pedido, null, null))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("CRIADO");
        assertThat(chamar(HttpMethod.POST, "/api/pagamentos/pedidos/" + pedido, null, null))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.POST, "/api/pagamentos/webhooks/" + gateway.webhookToken(), "{}", null))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Notificação não autenticada.");
        assertThat(chamar(HttpMethod.GET, "/api/pagamentos/webhooks/" + gateway.webhookToken(), null, null))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
