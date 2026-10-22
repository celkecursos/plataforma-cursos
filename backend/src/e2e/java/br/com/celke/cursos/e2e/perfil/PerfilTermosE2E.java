package br.com.celke.cursos.e2e.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import br.com.celke.cursos.e2e.support.Adm;
import br.com.celke.cursos.e2e.support.Api;
import br.com.celke.cursos.e2e.support.Contas;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Jornada: o ADM publica uma versão nova dos termos de uso e a área do aluno fica bloqueada até o aceite;
 * depois o aluno atualiza o perfil e troca a senha, encerrando a sessão antiga.
 */
class PerfilTermosE2E {

    @Test
    @DisplayName("novo termo publicado → rotas do aluno bloqueadas até o aceite; perfil e troca de senha")
    void termosEPerfil() {
        Adm adm = Adm.doSeed();
        String email = Contas.criarConfirmada();
        String token = Api.login(email, Contas.SENHA).jsonPath().getString("accessToken");
        aluno(token).get("/api/aluno/cursos").then().statusCode(200);

        // 1. Versão nova dos termos de uso: a área do aluno responde 403 com o termo pendente
        long termo = adm.criar(
                "/api/adm/termos", "{\"tipo\":\"TERMOS_USO\",\"conteudo\":\"Termos de uso **atualizados**.\"}");
        aluno(token)
                .get("/api/aluno/cursos")
                .then()
                .statusCode(403)
                .body("detail", equalTo("Aceite os termos atualizados para continuar."))
                .body("termosPendentes.id", equalTo(List.of((int) termo)));
        Api.site("aluno.localhost")
                .get("/api/termos/vigentes")
                .then()
                .statusCode(200)
                .body(
                        "find { it.id == %d }.conteudoHtml".formatted(termo),
                        equalTo("<p>Termos de uso <strong>atualizados</strong>.</p>"));

        // 2. Aceite libera
        aluno(token).post("/api/termos/" + termo + "/aceite").then().statusCode(204);
        aluno(token).get("/api/aluno/cursos").then().statusCode(200);

        // 3. Perfil: nome novo
        aluno(token)
                .body("{\"nome\":\"Aluna Renomeada\"}")
                .put("/api/perfil")
                .then()
                .statusCode(200)
                .body("nome", equalTo("Aluna Renomeada"));

        // 4. Troca de senha encerra a sessão: o refresh antigo não vale mais, a senha nova entra
        var sessao = Api.loginSemAceite(email, Contas.SENHA);
        String refresh = sessao.getCookie("refresh_token");
        aluno(token)
                .body("{\"senhaAtual\":\"%s\",\"novaSenha\":\"senha-nova-da-jornada\"}".formatted(Contas.SENHA))
                .put("/api/perfil/senha")
                .then()
                .statusCode(204);
        Api.aluno()
                .header("Origin", "http://aluno.localhost:8088")
                .cookie("refresh_token", refresh)
                .post("/api/auth/refresh")
                .then()
                .statusCode(401);
        assertThat(Api.loginSemAceite(email, "senha-nova-da-jornada").jsonPath().getString("accessToken"))
                .isNotBlank();
    }

    private static RequestSpecification aluno(String token) {
        return Api.aluno().header("Authorization", Api.bearer(token));
    }
}
