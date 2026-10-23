package br.com.celke.cursos.e2e.matricula;

import static org.hamcrest.Matchers.equalTo;

import br.com.celke.cursos.e2e.support.Adm;
import br.com.celke.cursos.e2e.support.Api;
import br.com.celke.cursos.e2e.support.Contas;
import br.com.celke.cursos.e2e.support.Dados;
import io.restassured.specification.RequestSpecification;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Jornada: aluno com conta nova vê só a aula gratuita; o ADM matricula e o aluno passa a ver a turma
 * e as aulas; com a matrícula vencida, o acesso termina e o curso vai para os expirados.
 */
class MatriculaE2E {

    @Test
    @DisplayName("sem matrícula 403, matriculado vê a turma, matrícula vencida 403")
    void acessoPelaMatricula() {
        Adm adm = Adm.doSeed();
        String slug = Dados.stamp();
        long curso = adm.criar(
                "/api/adm/cursos",
                "{\"titulo\":\"Curso %s\",\"slug\":\"%s\",\"resumo\":\"Matrícula e2e\"}".formatted(slug, slug));
        long turma = adm.criar("/api/adm/cursos/" + curso + "/turmas", "{\"nome\":\"Turma e2e\"}");
        adm.alterarStatus("/api/adm/turmas/" + turma + "/status", "ATIVA");
        long modulo = adm.criar("/api/adm/turmas/" + turma + "/modulos", "{\"titulo\":\"Módulo\"}");
        long gratuita =
                adm.criar("/api/adm/modulos/" + modulo + "/aulas", "{\"titulo\":\"Degustação\",\"gratuita\":true}");
        long paga = adm.criar("/api/adm/modulos/" + modulo + "/aulas", "{\"titulo\":\"Conteúdo pago\"}");
        adm.alterarStatus("/api/adm/cursos/" + curso + "/status", "PUBLICADO");

        String email = Contas.criarConfirmada();
        String token = Api.login(email, Contas.SENHA).jsonPath().getString("accessToken");

        // 1. Sem matrícula: só a aula gratuita
        aluno(token).get("/api/aluno/turmas/" + turma).then().statusCode(403);
        aluno(token).get("/api/aluno/aulas/" + paga).then().statusCode(403);
        aluno(token).get("/api/aluno/aulas/" + gratuita).then().statusCode(200).body("titulo", equalTo("Degustação"));

        // 2. O ADM matricula por 30 dias
        Instant agora = Instant.now();
        long matricula = adm.criar(
                "/api/adm/turmas/" + turma + "/matriculas",
                "{\"email\":\"%s\",\"formaPagamento\":\"CORTESIA\",\"fim\":\"%s\"}"
                        .formatted(email, agora.plus(Duration.ofDays(30))));

        aluno(token)
                .get("/api/aluno/cursos")
                .then()
                .statusCode(200)
                .body("cursoSlug", equalTo(List.of(slug)))
                .body("[0].situacao", equalTo("VIGENTE"));
        aluno(token)
                .get("/api/aluno/turmas/" + turma)
                .then()
                .statusCode(200)
                .body("modulos[0].aulas.titulo", equalTo(List.of("Degustação", "Conteúdo pago")));
        aluno(token).get("/api/aluno/aulas/" + paga).then().statusCode(200).body("titulo", equalTo("Conteúdo pago"));

        // 3. O período passa a ter terminado ontem: o acesso acaba na hora
        adm.requisicao()
                .body("{\"inicio\":\"%s\",\"fim\":\"%s\"}"
                        .formatted(agora.minus(Duration.ofDays(30)), agora.minus(Duration.ofDays(1))))
                .put("/api/adm/matriculas/" + matricula + "/periodo")
                .then()
                .statusCode(200)
                .body("situacao", equalTo("VENCIDA"));

        aluno(token).get("/api/aluno/turmas/" + turma).then().statusCode(403);
        aluno(token).get("/api/aluno/aulas/" + paga).then().statusCode(403);
        aluno(token).get("/api/aluno/cursos").then().statusCode(200).body("size()", equalTo(0));
        aluno(token)
                .get("/api/aluno/cursos?situacao=EXPIRADOS")
                .then()
                .statusCode(200)
                .body("cursoSlug", equalTo(List.of(slug)));
    }

    private static RequestSpecification aluno(String token) {
        return Api.aluno().header("Authorization", Api.bearer(token));
    }
}
