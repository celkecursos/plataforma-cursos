package br.com.celke.cursos.e2e.video;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.e2e.support.Adm;
import br.com.celke.cursos.e2e.support.Arquivos;
import br.com.celke.cursos.e2e.support.Dados;
import br.com.celke.cursos.e2e.support.Midia;
import br.com.celke.cursos.e2e.support.Videos;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.net.URI;
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

        // 2. Partes enviadas direto ao Garage pelo Caddy; a conclusão coloca o vídeo na fila
        Videos.enviar(adm, aula, video);

        // 3. O worker converte: status pronto, com o tamanho antes e depois
        JsonPath pronto = Videos.aguardarProcessamento(adm, aula);
        assertThat(pronto.getString("status"))
                .as("erro: %s", pronto.getString("erro"))
                .isEqualTo("PRONTO");
        assertThat(pronto.getLong("tamanhoOriginal")).isEqualTo(video.length);
        assertThat(pronto.getLong("tamanhoFinal")).isPositive();
        assertThat(pronto.getInt("duracaoSegundos")).isEqualTo(3);
        assertThat(pronto.getList("variantes", String.class)).containsExactly("720p");

        // 4. A thumbnail sai pelo host de mídia
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
