package br.com.celke.cursos.e2e.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import br.com.celke.cursos.e2e.support.Api;
import br.com.celke.cursos.e2e.support.Dados;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** O que o Caddy garante na frente da API: headers, rotas expostas e o IP real do cliente. */
class ProxyE2E {

    @Test
    @DisplayName("health check passa pelo Caddy com os headers de segurança e sem identificar o servidor")
    void headersDeSeguranca() {
        var resposta =
                Api.aluno().get("/actuator/health").then().statusCode(200).body("status", equalTo("UP"));

        resposta.header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "strict-origin-when-cross-origin")
                .header("Cross-Origin-Resource-Policy", "same-site")
                .header("Strict-Transport-Security", "max-age=31536000");
        assertThat(resposta.extract().header("Server")).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/v3/api-docs", "/swagger-ui/index.html", "/actuator/env", "/actuator/metrics", "/"})
    @DisplayName("fora de /api e do health check, nada chega à API: quem responde é o front")
    void rotasForaDaApi(String rota) {
        // O container web devolve o index.html do app (o React Router decide a página)
        Api.aluno()
                .get(rota)
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("id=\"raiz\""));
        Api.adm()
                .get(rota)
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("id=\"raiz\""));
    }

    @Test
    @DisplayName("os fronts saem com Content-Security-Policy e o index.html sempre revalidado")
    void cabecalhosDosFronts() {
        for (var cliente : List.of(Api.aluno(), Api.adm())) {
            cliente.get("/")
                    .then()
                    .statusCode(200)
                    .header("Content-Security-Policy", containsString("default-src 'self'"))
                    .header("Content-Security-Policy", containsString("frame-ancestors 'none'"))
                    .header("Cache-Control", "no-cache")
                    .header("Strict-Transport-Security", "max-age=31536000");
        }
    }

    @Test
    @DisplayName("aluno e ADM são apps diferentes")
    void appsSeparados() {
        Api.aluno().get("/").then().body(containsString("Área do aluno"));
        Api.adm().get("/").then().body(containsString("Administração"));
    }

    @Test
    @DisplayName("X-Forwarded-For forjado pelo cliente não muda o IP visto pela API")
    void xForwardedForForjado() {
        // Cada requisição finge vir de um IP diferente. Se a API acreditasse, cada uma cairia num
        // balde novo do limite por IP; como o Caddy substitui o cabeçalho, todas gastam o mesmo balde.
        int primeira = restanteNoLimite("203.0.113.1");
        restanteNoLimite("203.0.113.2");
        int terceira = restanteNoLimite("203.0.113.3");

        assertThat(terceira).isLessThan(primeira);
    }

    private static int restanteNoLimite(String ipForjado) {
        String restante = Api.aluno()
                .header("X-Forwarded-For", ipForjado)
                .body("{\"email\":\"%s\",\"senha\":\"qualquer-senha\"}".formatted(Dados.email()))
                .post("/api/auth/login")
                .then()
                .statusCode(401)
                .extract()
                .header("X-RateLimit-Remaining");
        return Integer.parseInt(restante);
    }
}
