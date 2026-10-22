package br.com.celke.cursos.e2e.support;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * Requisições à plataforma pelo Caddy, como um navegador faria. Todos os sites respondem na mesma
 * porta; o cabeçalho Host escolhe qual (sem depender da resolução de {@code *.localhost}).
 */
public final class Api {

    private Api() {}

    public static RequestSpecification aluno() {
        return site(E2eConfig.HOST_ALUNO);
    }

    public static RequestSpecification adm() {
        return site(E2eConfig.HOST_ADM);
    }

    public static RequestSpecification site(String host) {
        return given().baseUri("http://localhost")
                .port(E2eConfig.PORTA)
                .header("Host", host + ":" + E2eConfig.PORTA)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON);
    }

    /**
     * Login pela área do aluno; confere o 200, aceita os termos vigentes ainda pendentes (como o front faz
     * no primeiro acesso) e devolve a resposta do login (access token e cookie).
     */
    public static Response login(String email, String senha) {
        Response resposta = loginSemAceite(email, senha);
        aceitarTermos(resposta.jsonPath().getString("accessToken"));
        return resposta;
    }

    /** Login sem tocar nos termos (jornada que confere o bloqueio por termo pendente). */
    public static Response loginSemAceite(String email, String senha) {
        return aluno().body("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha))
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .response();
    }

    /** Aceita todos os termos vigentes que o usuário do token ainda não aceitou. */
    public static void aceitarTermos(String accessToken) {
        var pendentes = aluno().header("Authorization", bearer(accessToken))
                .get("/api/termos/pendentes")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("id", Long.class);
        for (Long id : pendentes) {
            aluno().header("Authorization", bearer(accessToken))
                    .post("/api/termos/" + id + "/aceite")
                    .then()
                    .statusCode(204);
        }
    }

    public static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
