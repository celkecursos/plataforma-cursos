package br.com.celke.cursos.e2e.pagamento;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import br.com.celke.cursos.e2e.support.Adm;
import br.com.celke.cursos.e2e.support.Api;
import br.com.celke.cursos.e2e.support.Contas;
import br.com.celke.cursos.e2e.support.Dados;
import br.com.celke.cursos.e2e.support.Hotmart;
import br.com.celke.cursos.e2e.support.Mailpit;
import io.restassured.specification.RequestSpecification;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Jornada: o ADM configura a Hotmart e a oferta pela API; o aluno faz o checkout e recebe a URL do
 * pay.hotmart.com com o código do pedido; a notificação aprovada chega pelo Caddy, o worker processa,
 * o aluno passa a ver a turma e o e-mail de confirmação chega ao Mailpit; o reembolso tira o acesso.
 */
class PagamentoE2E {

    @Test
    @DisplayName("checkout → PURCHASE_APPROVED pelo Caddy → acesso e e-mail → PURCHASE_REFUNDED tira o acesso")
    void compraPelaHotmart() {
        Adm adm = Adm.doSeed();
        String slug = Dados.stamp();
        long curso = adm.criar(
                "/api/adm/cursos",
                "{\"titulo\":\"Curso %s\",\"slug\":\"%s\",\"resumo\":\"Pagamento e2e\"}".formatted(slug, slug));
        long turma = adm.criar("/api/adm/cursos/" + curso + "/turmas", "{\"nome\":\"Turma e2e\"}");
        adm.alterarStatus("/api/adm/turmas/" + turma + "/status", "ATIVA");
        adm.alterarStatus("/api/adm/cursos/" + curso + "/status", "PUBLICADO");

        // 1. Hotmart configurada e ativa; oferta da turma
        String hottok = "hottok-" + Dados.stamp();
        var gateway = adm.requisicao()
                .body("{\"tipo\":\"HOTMART\",\"nome\":\"Hotmart e2e\",\"valores\":{\"hottok\":\"%s\"}}"
                        .formatted(hottok))
                .post("/api/adm/pagamentos/gateways")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath();
        long gatewayId = gateway.getLong("id");
        String webhookUrl = gateway.getString("webhookUrl");
        adm.requisicao()
                .post("/api/adm/pagamentos/gateways/" + gatewayId + "/ativacao")
                .then()
                .statusCode(200)
                .body("ativo", equalTo(true));
        String produto = Dados.stamp();
        long oferta = adm.criar(
                "/api/adm/turmas/" + turma + "/ofertas",
                "{\"produtoExterno\":\"%s\",\"ofertaExterna\":\"e2e\",\"titulo\":\"À vista\",\"precoCentavos\":19700,\"moeda\":\"BRL\"}"
                        .formatted(produto));

        // 2. Checkout do aluno: URL da Hotmart com o código do pedido
        String email = Contas.criarConfirmada();
        String token = Api.login(email, Contas.SENHA).jsonPath().getString("accessToken");
        String pedido = aluno(token)
                .body("{\"ofertaId\":%d}".formatted(oferta))
                .post("/api/pagamentos/checkout")
                .then()
                .statusCode(201)
                .body("tipo", equalTo("REDIRECIONAMENTO"))
                .body("url", startsWith("https://pay.hotmart.com/" + produto + "?off=e2e&email="))
                .extract()
                .jsonPath()
                .getString("pedido");
        aluno(token).get("/api/aluno/turmas/" + turma).then().statusCode(403);

        // 3. A Hotmart avisa a aprovação, pelo endereço que o painel mostrou (via Caddy)
        String caminho = webhookUrl.substring(webhookUrl.indexOf("/api/"));
        String transacao = "HP" + Dados.stamp();
        Instant agora = Instant.now();
        notificar(
                caminho,
                hottok,
                Hotmart.notificacao("PURCHASE_APPROVED", pedido, transacao, produto, agora.toEpochMilli()));

        await("matrícula pelo pagamento")
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> aluno(token).get("/api/aluno/turmas/" + turma).statusCode() == 200);
        aluno(token)
                .get("/api/pagamentos/pedidos/" + pedido)
                .then()
                .statusCode(200)
                .body("status", equalTo("APROVADO"));
        Mailpit.aguardar(email, "Sua matrícula foi confirmada");

        // 4. Reembolso: o acesso termina
        notificar(
                caminho,
                hottok,
                Hotmart.notificacao(
                        "PURCHASE_REFUNDED",
                        pedido,
                        transacao,
                        produto,
                        agora.plusSeconds(60).toEpochMilli()));
        await("acesso retirado pelo reembolso")
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> aluno(token).get("/api/aluno/turmas/" + turma).statusCode() == 403);
        Api.aluno()
                .get("/api/pagamentos/pedidos/" + pedido)
                .then()
                .statusCode(200)
                .body("status", equalTo("REEMBOLSADO"));
    }

    private static void notificar(String caminho, String hottok, String corpo) {
        Api.aluno()
                .header("X-HOTMART-HOTTOK", hottok)
                .body(corpo)
                .post(caminho)
                .then()
                .statusCode(200)
                .body("resultado", equalTo("recebido"));
    }

    private static RequestSpecification aluno(String token) {
        return Api.aluno().header("Authorization", Api.bearer(token));
    }
}
