package br.com.celke.cursos.e2e.arquivo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.e2e.support.Adm;
import br.com.celke.cursos.e2e.support.Api;
import br.com.celke.cursos.e2e.support.Contas;
import br.com.celke.cursos.e2e.support.Dados;
import br.com.celke.cursos.e2e.support.Midia;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Jornada: o ADM envia o código-fonte da aula direto ao Garage pelo Caddy e publica; o aluno sem
 * matrícula recebe 403; matriculado, baixa o arquivo pelo host de mídia como anexo.
 */
class ArquivoE2E {

    private static final byte[] ZIP = {'P', 'K', 3, 4, 20, 0, 0, 0, 8, 0, 'e', '2', 'e'};

    @Test
    @DisplayName("download do arquivo de apoio: bloqueado sem matrícula, liberado com matrícula")
    void downloadPermitidoEBloqueado() {
        Adm adm = Adm.doSeed();
        String slug = Dados.stamp();

        // 1. Curso publicado com turma ativa e uma aula paga
        long curso = adm.criar(
                "/api/adm/cursos",
                "{\"titulo\":\"Curso %s\",\"slug\":\"%s\",\"resumo\":\"Arquivos e2e\"}".formatted(slug, slug));
        long turma = adm.criar("/api/adm/cursos/" + curso + "/turmas", "{\"nome\":\"Turma e2e\"}");
        adm.alterarStatus("/api/adm/turmas/" + turma + "/status", "ATIVA");
        long modulo = adm.criar("/api/adm/turmas/" + turma + "/modulos", "{\"titulo\":\"Módulo\"}");
        long aula = adm.criar("/api/adm/modulos/" + modulo + "/aulas", "{\"titulo\":\"Aula com código\"}");
        adm.alterarStatus("/api/adm/cursos/" + curso + "/status", "PUBLICADO");

        // 2. Envio: URL pré-assinada → PUT no Garage pelo Caddy → confirmação na API
        JsonPath envio = adm.requisicao()
                .body("{\"contentType\":\"application/zip\",\"tamanho\":%d}".formatted(ZIP.length))
                .post("/api/adm/aulas/" + aula + "/arquivos/envio")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath();
        URI url = URI.create(envio.getString("url"));
        assertThat(url.getHost()).isEqualTo(Midia.HOST);
        Midia.requisicao()
                .contentType("application/zip")
                .body(ZIP)
                .put(Midia.caminho(url))
                .then()
                .statusCode(200);
        long arquivo = adm.criar(
                "/api/adm/aulas/" + aula + "/arquivos",
                "{\"chave\":\"%s\",\"titulo\":\"Código-fonte\",\"nomeArquivo\":\"codigo.zip\"}"
                        .formatted(envio.getString("chave")));

        // 3. Aluno sem matrícula: 403
        String email = Contas.criarConfirmada();
        String token = Api.login(email, Contas.SENHA).jsonPath().getString("accessToken");
        aluno(token).get("/api/aluno/arquivos/" + arquivo + "/download").then().statusCode(403);

        // 4. Matriculado: URL de download no host de mídia, baixando o mesmo conteúdo como anexo
        adm.criar(
                "/api/adm/turmas/" + turma + "/matriculas",
                "{\"email\":\"%s\",\"formaPagamento\":\"CORTESIA\",\"fim\":\"%s\"}"
                        .formatted(email, Instant.now().plus(Duration.ofDays(30))));
        URI download = URI.create(aluno(token)
                .get("/api/aluno/arquivos/" + arquivo + "/download")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("url"));
        assertThat(download.getHost()).isEqualTo(Midia.HOST);
        Response baixado = Midia.requisicao()
                .get(Midia.caminho(download))
                .then()
                .statusCode(200)
                .extract()
                .response();
        assertThat(baixado.asByteArray()).isEqualTo(ZIP);
        assertThat(baixado.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"codigo.zip\"");
    }

    private static RequestSpecification aluno(String token) {
        return Api.aluno().header("Authorization", Api.bearer(token));
    }
}
