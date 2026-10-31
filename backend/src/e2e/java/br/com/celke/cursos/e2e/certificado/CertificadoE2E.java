package br.com.celke.cursos.e2e.certificado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import br.com.celke.cursos.e2e.support.Adm;
import br.com.celke.cursos.e2e.support.Api;
import br.com.celke.cursos.e2e.support.Contas;
import br.com.celke.cursos.e2e.support.Dados;
import br.com.celke.cursos.e2e.support.Midia;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Jornada: o aluno conclui a turma, emite o certificado, baixa o PDF e qualquer pessoa valida o código. */
class CertificadoE2E {

    @Test
    @DisplayName("aluno conclui → gera certificado → código valida")
    void concluirEmitirValidar() {
        Adm adm = Adm.doSeed();
        String slug = Dados.stamp();

        // 1. Curso publicado com uma aula; certificado exige 100% e informa 8 horas
        long curso = adm.criar(
                "/api/adm/cursos",
                "{\"titulo\":\"Curso %s\",\"slug\":\"%s\",\"resumo\":\"Certificado e2e\"}".formatted(slug, slug));
        long turma = adm.criar("/api/adm/cursos/" + curso + "/turmas", "{\"nome\":\"Turma e2e\"}");
        adm.alterarStatus("/api/adm/turmas/" + turma + "/status", "ATIVA");
        adm.requisicao()
                .body("{\"percentualConclusao\":100,\"cargaHorariaHoras\":8}")
                .put("/api/adm/turmas/" + turma + "/certificado")
                .then()
                .statusCode(200);
        long modulo = adm.criar("/api/adm/turmas/" + turma + "/modulos", "{\"titulo\":\"Módulo\"}");
        long aula = adm.criar("/api/adm/modulos/" + modulo + "/aulas", "{\"titulo\":\"Única aula\"}");
        adm.alterarStatus("/api/adm/cursos/" + curso + "/status", "PUBLICADO");
        String email = Contas.criarConfirmada();
        String token = Api.login(email, Contas.SENHA).jsonPath().getString("accessToken");
        adm.criar(
                "/api/adm/turmas/" + turma + "/matriculas",
                "{\"email\":\"%s\",\"formaPagamento\":\"CORTESIA\",\"fim\":\"%s\"}"
                        .formatted(email, Instant.now().plus(Duration.ofDays(30))));

        // 2. Conclui: a única aula vista
        aluno(token)
                .body("{\"vista\":true}")
                .patch("/api/aluno/aulas/" + aula + "/progresso")
                .then()
                .statusCode(200);
        aluno(token)
                .get("/api/aluno/turmas/" + turma + "/certificado")
                .then()
                .statusCode(200)
                .body("concluiu", equalTo(true));

        // 3. Emite com o nome confirmado
        Response emitido = aluno(token)
                .body("{\"nomeCompleto\":\"Aluna da Jornada\"}")
                .post("/api/aluno/turmas/" + turma + "/certificado")
                .then()
                .statusCode(201)
                .extract()
                .response();
        String codigo = emitido.jsonPath().getString("codigo");
        long id = emitido.jsonPath().getLong("id");

        // 4. Baixa o PDF pelo host de mídia
        URI url = URI.create(aluno(token)
                .get("/api/aluno/certificados/" + id + "/download")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("url"));
        byte[] pdf = Midia.requisicao()
                .get(Midia.caminho(url))
                .then()
                .statusCode(200)
                .extract()
                .asByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        // 5. Qualquer pessoa valida o código, sem login, pelo site
        Api.site("aluno.localhost")
                .get("/api/certificados/" + codigo)
                .then()
                .statusCode(200)
                .body("nomeCompleto", equalTo("Aluna da Jornada"))
                .body("cursoTitulo", equalTo("Curso " + slug))
                .body("cargaHorariaHoras", equalTo(8));
    }

    private static RequestSpecification aluno(String token) {
        return Api.aluno().header("Authorization", Api.bearer(token));
    }
}
