package br.com.celke.cursos.e2e.forum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import br.com.celke.cursos.e2e.support.Adm;
import br.com.celke.cursos.e2e.support.Api;
import br.com.celke.cursos.e2e.support.Contas;
import br.com.celke.cursos.e2e.support.Dados;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Jornada: o aluno matriculado pergunta no fórum da aula, o ADM responde como instrutor e o aluno vê a resposta. */
class ForumE2E {

    @Test
    @DisplayName("aluno pergunta, instrutor responde, aluno vê a resposta")
    void perguntaERespostaDoInstrutor() {
        Adm adm = Adm.doSeed();
        String slug = Dados.stamp();

        // 1. Curso publicado com turma ativa e uma aula; aluno novo matriculado
        long curso = adm.criar(
                "/api/adm/cursos",
                "{\"titulo\":\"Curso %s\",\"slug\":\"%s\",\"resumo\":\"Fórum e2e\"}".formatted(slug, slug));
        long turma = adm.criar("/api/adm/cursos/" + curso + "/turmas", "{\"nome\":\"Turma e2e\"}");
        adm.alterarStatus("/api/adm/turmas/" + turma + "/status", "ATIVA");
        long modulo = adm.criar("/api/adm/turmas/" + turma + "/modulos", "{\"titulo\":\"Módulo\"}");
        long aula = adm.criar("/api/adm/modulos/" + modulo + "/aulas", "{\"titulo\":\"Aula\"}");
        adm.alterarStatus("/api/adm/cursos/" + curso + "/status", "PUBLICADO");
        String email = Contas.criarConfirmada();
        String token = Api.login(email, Contas.SENHA).jsonPath().getString("accessToken");
        adm.criar(
                "/api/adm/turmas/" + turma + "/matriculas",
                "{\"email\":\"%s\",\"formaPagamento\":\"CORTESIA\",\"fim\":\"%s\"}"
                        .formatted(email, Instant.now().plus(Duration.ofDays(30))));

        // 2. Pergunta em Markdown, com HTML malicioso que não pode voltar
        JsonPath pergunta = aluno(token)
                .body(
                        "{\"titulo\":\"Erro ao compilar\",\"aulaId\":%d,\"corpo\":\"Aparece **erro** <script>alert(1)</script>\"}"
                                .formatted(aula))
                .post("/api/aluno/turmas/" + turma + "/forum")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath();
        long topico = pergunta.getLong("id");
        assertThat(pergunta.getString("corpoHtml"))
                .contains("<strong>erro</strong>")
                .doesNotContain("<script");

        // 3. O ADM vê a pergunta na moderação e responde como instrutor
        adm.requisicao()
                .get("/api/adm/turmas/" + turma + "/forum")
                .then()
                .statusCode(200)
                .body("itens[0].id", equalTo((int) topico));
        adm.requisicao()
                .body("{\"corpo\":\"Confira o import da classe.\"}")
                .post("/api/adm/forum/topicos/" + topico + "/respostas")
                .then()
                .statusCode(200)
                .body("respondidoInstrutor", equalTo(true));

        // 4. O aluno vê a resposta do instrutor
        aluno(token)
                .get("/api/aluno/forum/topicos/" + topico)
                .then()
                .statusCode(200)
                .body("respostas[0].instrutor", equalTo(true))
                .body("respostas[0].corpoHtml", equalTo("<p>Confira o import da classe.</p>"));
        aluno(token)
                .get("/api/aluno/turmas/" + turma + "/forum")
                .then()
                .statusCode(200)
                .body("itens[0].respondidoInstrutor", equalTo(true))
                .body("itens[0].respostas", equalTo(1));
    }

    private static RequestSpecification aluno(String token) {
        return Api.aluno().header("Authorization", Api.bearer(token));
    }
}
