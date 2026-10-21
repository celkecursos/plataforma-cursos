package br.com.celke.cursos.curso;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Monta cursos, turmas, módulos e aulas pela API do ADM, para os testes do módulo curso. */
final class CursoITHelper {

    private final MockMvcTester mvc;
    private final String bearer;

    CursoITHelper(MockMvcTester mvc, String bearer) {
        this.mvc = mvc;
        this.bearer = bearer;
    }

    MvcTestResult enviar(String metodo, String uri, String corpo) {
        var requisicao = mvc.method(org.springframework.http.HttpMethod.valueOf(metodo))
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer);
        if (corpo != null) {
            requisicao = requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        return requisicao.exchange();
    }

    long criarCurso(String titulo, String slug) {
        String corpo = slug == null
                ? "{\"titulo\":\"%s\",\"resumo\":\"Resumo de %s\"}".formatted(titulo, titulo)
                : "{\"titulo\":\"%s\",\"slug\":\"%s\",\"resumo\":\"Resumo de %s\"}".formatted(titulo, slug, titulo);
        return idCriado(enviar("POST", "/api/adm/cursos", corpo));
    }

    void publicar(long cursoId) {
        status("/api/adm/cursos/" + cursoId + "/status", "PUBLICADO");
    }

    long criarTurma(long cursoId, String nome) {
        return idCriado(enviar("POST", "/api/adm/cursos/" + cursoId + "/turmas", "{\"nome\":\"%s\"}".formatted(nome)));
    }

    void ativarTurma(long turmaId) {
        status("/api/adm/turmas/" + turmaId + "/status", "ATIVA");
    }

    long criarModulo(long turmaId, String titulo) {
        return idCriado(
                enviar("POST", "/api/adm/turmas/" + turmaId + "/modulos", "{\"titulo\":\"%s\"}".formatted(titulo)));
    }

    long criarAula(long moduloId, String titulo, boolean gratuita) {
        return idCriado(enviar(
                "POST",
                "/api/adm/modulos/" + moduloId + "/aulas",
                "{\"titulo\":\"%s\",\"gratuita\":%s}".formatted(titulo, gratuita)));
    }

    /** {@code PATCH <rota>} com o novo status; confere o 200. */
    void status(String rota, String status) {
        assertThat(enviar("PATCH", rota, "{\"status\":\"%s\"}".formatted(status)))
                .hasStatusOk();
    }

    static long idCriado(MvcTestResult resposta) {
        assertThat(resposta).hasStatus(HttpStatus.CREATED);
        return ((Number) JsonPath.read(corpo(resposta), "$.id")).longValue();
    }

    static String corpo(MvcTestResult resposta) {
        try {
            return resposta.getResponse().getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
