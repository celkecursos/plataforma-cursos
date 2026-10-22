package br.com.celke.cursos.e2e.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

import br.com.celke.cursos.e2e.support.Adm;
import br.com.celke.cursos.e2e.support.Api;
import br.com.celke.cursos.e2e.support.Arquivos;
import br.com.celke.cursos.e2e.support.Contas;
import br.com.celke.cursos.e2e.support.Dados;
import br.com.celke.cursos.e2e.support.Midia;
import br.com.celke.cursos.e2e.support.Videos;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Jornada: o aluno matriculado abre o vídeo da aula pela API (playlist assinada), baixa um segmento
 * direto do Garage pelo Caddy, a URL do segmento vence e é recusada (a stack de e2e usa margem de 5 s) e o progresso
 * marcado na aula aparece no percentual da turma.
 */
class EntregaVideoE2E {

    @Test
    @DisplayName("playlist assinada → segmento baixa → depois de vencer, recusado; progresso refletido na turma")
    void assistirEProgredir() {
        Adm adm = Adm.doSeed();
        String slug = Dados.stamp();

        // 1. Curso publicado, turma ativa, uma aula com vídeo convertido
        long curso = adm.criar(
                "/api/adm/cursos",
                "{\"titulo\":\"Curso %s\",\"slug\":\"%s\",\"resumo\":\"Entrega e2e\"}".formatted(slug, slug));
        long turma = adm.criar("/api/adm/cursos/" + curso + "/turmas", "{\"nome\":\"Turma e2e\"}");
        adm.alterarStatus("/api/adm/turmas/" + turma + "/status", "ATIVA");
        long modulo = adm.criar("/api/adm/turmas/" + turma + "/modulos", "{\"titulo\":\"Módulo\"}");
        long aula = adm.criar("/api/adm/modulos/" + modulo + "/aulas", "{\"titulo\":\"Aula em vídeo\"}");
        adm.alterarStatus("/api/adm/cursos/" + curso + "/status", "PUBLICADO");
        Videos.enviar(adm, aula, Arquivos.amostra("video-3s.mp4"));
        assertThat(Videos.aguardarProcessamento(adm, aula).getString("status")).isEqualTo("PRONTO");

        // 2. Aluno sem matrícula não abre o vídeo
        String email = Contas.criarConfirmada();
        String token = Api.login(email, Contas.SENHA).jsonPath().getString("accessToken");
        player(token).get("/api/aulas/" + aula + "/video.m3u8").then().statusCode(403);

        // 3. Matriculado: master pela API, apontando para a variante também servida pela API
        adm.criar(
                "/api/adm/turmas/" + turma + "/matriculas",
                "{\"email\":\"%s\",\"formaPagamento\":\"CORTESIA\",\"fim\":\"%s\"}"
                        .formatted(email, Instant.now().plus(Duration.ofDays(30))));
        Response master = player(token)
                .get("/api/aulas/" + aula + "/video.m3u8")
                .then()
                .statusCode(200)
                .header("Cache-Control", "no-store")
                .extract()
                .response();
        assertThat(master.getContentType()).startsWith("application/vnd.apple.mpegurl");
        assertThat(master.asString()).contains("\nvideo/720p.m3u8");

        // 4. Variante: segmentos com URLs pré-assinadas no host de mídia
        List<String> segmentos = player(token)
                .get("/api/aulas/" + aula + "/video/720p.m3u8")
                .then()
                .statusCode(200)
                .extract()
                .asString()
                .lines()
                .filter(linha -> !linha.startsWith("#") && !linha.isBlank())
                .toList();
        assertThat(segmentos).isNotEmpty();
        URI segmento = URI.create(segmentos.getFirst());
        assertThat(segmento.getHost()).isEqualTo(Midia.HOST);

        // 5. O segmento baixa direto do Garage pelo Caddy (MPEG-TS começa com 0x47)...
        byte[] bytes = Midia.requisicao()
                .get(Midia.caminho(segmento))
                .then()
                .statusCode(200)
                .extract()
                .asByteArray();
        assertThat(bytes[0]).isEqualTo((byte) 0x47);

        // 6. ...e para de baixar quando a validade (3 s de vídeo + 5 s de margem) vence. O Garage recusa
        // com 400 "Date is too old" (a AWS responderia 403): a jornada aceita as duas recusas do S3
        Response vencida = await("URL do segmento vencida")
                .atMost(Duration.ofSeconds(40))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> Midia.requisicao().get(Midia.caminho(segmento)), r -> r.statusCode() != 200);
        assertThat(vencida.statusCode()).isIn(400, 403);
        assertThat(vencida.asString()).containsAnyOf("Date is too old", "Request has expired");

        // 7. Progresso: posição e aula vista aparecem na turma
        aluno(token)
                .body("{\"posicaoSegundos\":3,\"vista\":true}")
                .patch("/api/aluno/aulas/" + aula + "/progresso")
                .then()
                .statusCode(200)
                .body("vista", equalTo(true));
        aluno(token)
                .get("/api/aluno/turmas/" + turma + "/progresso")
                .then()
                .statusCode(200)
                .body("percentual", equalTo(100))
                .body("aulas[0].posicaoSegundos", equalTo(3));
    }

    private static RequestSpecification aluno(String token) {
        return Api.aluno().header("Authorization", Api.bearer(token));
    }

    /** Como o player (hls.js): aceita qualquer tipo, já que as playlists não são JSON. */
    private static RequestSpecification player(String token) {
        return aluno(token).accept("*/*");
    }
}
