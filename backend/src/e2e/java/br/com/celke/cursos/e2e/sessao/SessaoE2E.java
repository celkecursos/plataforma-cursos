package br.com.celke.cursos.e2e.sessao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import br.com.celke.cursos.e2e.support.Api;
import br.com.celke.cursos.e2e.support.Contas;
import br.com.celke.cursos.e2e.support.E2eConfig;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Jornada: login → refresh → logout atravessando o Caddy, com o cookie de refresh e a verificação de
 * origem funcionando atrás do proxy.
 */
class SessaoE2E {

    @Test
    @DisplayName("login, refresh e logout pelo Caddy com o cookie preso ao host")
    void loginRefreshLogout() {
        String email = Contas.criarConfirmada();

        // 1. Login: access token no corpo, refresh token só no cookie (sem Domain)
        Response login = Api.login(email, Contas.SENHA);
        String setCookie = login.getHeader("Set-Cookie");
        assertThat(setCookie)
                .startsWith("refresh_token=")
                .contains("HttpOnly", "SameSite=Strict", "Path=/api/auth")
                .doesNotContainIgnoringCase("Domain=");
        String token = login.jsonPath().getString("accessToken");
        String cookie = login.getCookie("refresh_token");

        // 2. O access token vale pelos dois hosts (mesma API atrás do Caddy)
        Api.adm()
                .header("Authorization", Api.bearer(token))
                .get("/api/auth/me")
                .then()
                .statusCode(200)
                .body("email", equalTo(email));

        // 3. Refresh vindo da área do aluno troca o cookie
        Response renovado = Api.aluno()
                .header("Origin", E2eConfig.origem(E2eConfig.HOST_ALUNO))
                .cookie("refresh_token", cookie)
                .post("/api/auth/refresh")
                .then()
                .statusCode(200)
                .extract()
                .response();
        String cookieNovo = renovado.getCookie("refresh_token");
        assertThat(cookieNovo).isNotEqualTo(cookie);

        // 4. Refresh vindo de outra aplicação do mesmo domínio é recusado
        Api.aluno()
                .header("Origin", "http://outra-app.localhost:" + E2eConfig.PORTA)
                .cookie("refresh_token", cookieNovo)
                .post("/api/auth/refresh")
                .then()
                .statusCode(403)
                .body("detail", equalTo("Origem não permitida."));

        // 5. Logout encerra a sessão
        Api.aluno()
                .header("Origin", E2eConfig.origem(E2eConfig.HOST_ALUNO))
                .cookie("refresh_token", cookieNovo)
                .post("/api/auth/logout")
                .then()
                .statusCode(204);
        Api.aluno()
                .cookie("refresh_token", cookieNovo)
                .post("/api/auth/refresh")
                .then()
                .statusCode(401);
    }
}
