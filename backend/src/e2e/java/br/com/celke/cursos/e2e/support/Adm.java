package br.com.celke.cursos.e2e.support;

import static org.hamcrest.Matchers.equalTo;

import io.restassured.specification.RequestSpecification;

/** Gestão pela área do ADM com o token de um admin: cria registros e troca status, conferindo a resposta. */
public final class Adm {

    private final String token;

    private Adm(String token) {
        this.token = token;
    }

    /** Login com o admin do seed da stack de e2e. */
    public static Adm doSeed() {
        return new Adm(
                Api.login(E2eConfig.ADMIN_SEED, E2eConfig.SENHA_SEED).jsonPath().getString("accessToken"));
    }

    public RequestSpecification requisicao() {
        return Api.adm().header("Authorization", Api.bearer(token));
    }

    /** {@code POST} que cria um registro; confere o 201 e devolve o id. */
    public long criar(String rota, String corpo) {
        return requisicao()
                .body(corpo)
                .post(rota)
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getLong("id");
    }

    /** {@code PATCH <rota>} com o novo status; confere o 200. */
    public void alterarStatus(String rota, String status) {
        requisicao()
                .body("{\"status\":\"%s\"}".formatted(status))
                .patch(rota)
                .then()
                .statusCode(200)
                .body("status", equalTo(status));
    }
}
