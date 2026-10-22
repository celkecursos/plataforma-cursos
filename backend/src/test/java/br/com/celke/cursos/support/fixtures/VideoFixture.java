package br.com.celke.cursos.support.fixtures;

import br.com.celke.cursos.video.domain.ResultadoTranscodificacao;
import br.com.celke.cursos.video.domain.Video;
import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;

/** Fábrica de vídeos para testes unitários (sem banco: o id é preenchido aqui). */
public final class VideoFixture {

    public static final Instant CRIADO_EM = Instant.parse("2026-01-01T00:00:00Z");
    public static final String UPLOAD = "uploads/videos/9/original";
    public static final String PLAYLIST = "videos/9/1/master.m3u8";

    private VideoFixture() {}

    /** Envio aberto (upload em partes {@code upload-1}) de um arquivo de 1000 bytes na aula 9. */
    public static Video aguardandoEnvio(long id) {
        Video video = new Video(9L, CRIADO_EM);
        ReflectionTestUtils.setField(video, "id", id);
        video.iniciarEnvio(UPLOAD, "upload-1", "aula.mp4", 1000, CRIADO_EM);
        return video;
    }

    public static Video pendente(long id) {
        Video video = aguardandoEnvio(id);
        video.enfileirar(1000, CRIADO_EM);
        return video;
    }

    public static Video comErro(long id) {
        Video video = pendente(id);
        video.falhar("falhou", CRIADO_EM);
        return video;
    }

    /** Convertido, com o HLS em {@link #PLAYLIST}. */
    public static Video pronto(long id) {
        Video video = pendente(id);
        video.iniciarProcessamento(CRIADO_EM);
        video.concluir(
                new ResultadoTranscodificacao(PLAYLIST, "videos/9/1/thumbnail.jpg", 500, 3, 1280, 720, "720p", 1000),
                CRIADO_EM);
        return video;
    }
}
