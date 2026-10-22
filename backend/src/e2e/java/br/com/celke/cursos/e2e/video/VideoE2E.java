package br.com.celke.cursos.e2e.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

import br.com.celke.cursos.e2e.support.Adm;
import br.com.celke.cursos.e2e.support.Arquivos;
import br.com.celke.cursos.e2e.support.Dados;
import br.com.celke.cursos.e2e.support.Midia;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Jornada: o ADM envia o vídeo de uma aula em partes, direto ao Garage pelo Caddy; o worker converte
 * com o ffmpeg e o vídeo fica pronto, com o tamanho antes e depois e a thumbnail.
 */
class VideoE2E {

    @Test
    @DisplayName("upload em partes de um vídeo curto até o status pronto")
    void uploadAtePronto() {
        Adm adm = Adm.doSeed();
        String slug = Dados.stamp();
        byte[] video = Arquivos.amostra("video-3s.mp4");

        // 1. Curso, turma, módulo e aula
        long curso = adm.criar(
                "/api/adm/cursos",
                "{\"titulo\":\"Curso %s\",\"slug\":\"%s\",\"resumo\":\"Jornada e2e\"}".formatted(slug, slug));
        long turma = adm.criar("/api/adm/cursos/" + curso + "/turmas", "{\"nome\":\"Turma e2e\"}");
        long modulo = adm.criar("/api/adm/turmas/" + turma + "/modulos", "{\"titulo\":\"Módulo\"}");
        long aula = adm.criar("/api/adm/modulos/" + modulo + "/aulas", "{\"titulo\":\"Aula em vídeo\"}");
        String rota = "/api/adm/aulas/" + aula + "/video";

        // 2. URLs pré-assinadas de cada parte, no host de mídia
        JsonPath envio = adm.requisicao()
                .body("{\"nomeArquivo\":\"aula.mp4\",\"contentType\":\"video/mp4\",\"tamanho\":%d}"
                        .formatted(video.length))
                .post(rota + "/envio")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath();
        int tamanhoParte = envio.getInt("tamanhoParte");
        List<String> urls = envio.getList("partes.url", String.class);

        // 3. PUT de cada parte direto no Garage pelo Caddy, guardando o ETag
        List<Map<String, Object>> partes = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++) {
            URI url = URI.create(urls.get(i));
            assertThat(url.getHost()).isEqualTo(Midia.HOST);
            int de = i * tamanhoParte;
            int ate = Math.min(video.length, de + tamanhoParte);
            byte[] parte = Arrays.copyOfRange(video, de, ate);
            Response put = Midia.requisicao()
                    .body(parte)
                    .put(Midia.caminho(url))
                    .then()
                    .statusCode(200)
                    .extract()
                    .response();
            partes.add(Map.of("numero", i + 1, "etag", put.getHeader("ETag")));
        }

        // 4. Conclusão: a API confere o arquivo e coloca na fila
        adm.requisicao()
                .body(Map.of("partes", partes))
                .post(rota + "/envio/conclusao")
                .then()
                .statusCode(202)
                .body("status", equalTo("PENDENTE"));

        // 5. O worker converte: status pronto, com o tamanho antes e depois
        JsonPath pronto = await("vídeo processado pelo worker")
                .atMost(Duration.ofMinutes(3))
                .pollInterval(Duration.ofSeconds(2))
                .until(
                        () -> adm.requisicao()
                                .get(rota)
                                .then()
                                .statusCode(200)
                                .extract()
                                .jsonPath(),
                        json -> !List.of("PENDENTE", "PROCESSANDO").contains(json.getString("status")));
        assertThat(pronto.getString("status"))
                .as("erro: %s", pronto.getString("erro"))
                .isEqualTo("PRONTO");
        assertThat(pronto.getLong("tamanhoOriginal")).isEqualTo(video.length);
        assertThat(pronto.getLong("tamanhoFinal")).isPositive();
        assertThat(pronto.getInt("duracaoSegundos")).isEqualTo(3);
        assertThat(pronto.getList("variantes", String.class)).containsExactly("720p");

        // 6. A thumbnail sai pelo host de mídia
        URI thumbnail = URI.create(pronto.getString("thumbnailUrl"));
        assertThat(thumbnail.getHost()).isEqualTo(Midia.HOST);
        Response imagem = Midia.requisicao()
                .get(Midia.caminho(thumbnail))
                .then()
                .statusCode(200)
                .extract()
                .response();
        assertThat(imagem.getContentType()).isEqualTo("image/jpeg");
        assertThat(imagem.asByteArray()).startsWith((byte) 0xFF, (byte) 0xD8);
    }
}
