package br.com.celke.cursos.e2e.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Vídeo da aula pelo caminho real: envio em partes pelo Caddy direto ao Garage e conversão pelo worker. */
public final class Videos {

    private Videos() {}

    /**
     * Pede as URLs das partes, faz o PUT de cada parte no host de mídia guardando o ETag e conclui o
     * envio (202, vídeo na fila).
     */
    public static void enviar(Adm adm, long aula, byte[] video) {
        String rota = "/api/adm/aulas/" + aula + "/video";
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

        List<Map<String, Object>> partes = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++) {
            URI url = URI.create(urls.get(i));
            assertThat(url.getHost()).isEqualTo(Midia.HOST);
            int de = i * tamanhoParte;
            int ate = Math.min(video.length, de + tamanhoParte);
            Response put = Midia.requisicao()
                    .body(Arrays.copyOfRange(video, de, ate))
                    .put(Midia.caminho(url))
                    .then()
                    .statusCode(200)
                    .extract()
                    .response();
            partes.add(Map.of("numero", i + 1, "etag", put.getHeader("ETag")));
        }

        adm.requisicao()
                .body(Map.of("partes", partes))
                .post(rota + "/envio/conclusao")
                .then()
                .statusCode(202)
                .body("status", equalTo("PENDENTE"));
    }

    /** Espera o worker terminar e devolve o vídeo da aula (pronto ou com erro). */
    public static JsonPath aguardarProcessamento(Adm adm, long aula) {
        return await("vídeo processado pelo worker")
                .atMost(Duration.ofMinutes(3))
                .pollInterval(Duration.ofSeconds(2))
                .until(
                        () -> adm.requisicao()
                                .get("/api/adm/aulas/" + aula + "/video")
                                .then()
                                .statusCode(200)
                                .extract()
                                .jsonPath(),
                        json -> !List.of("PENDENTE", "PROCESSANDO").contains(json.getString("status")));
    }
}
