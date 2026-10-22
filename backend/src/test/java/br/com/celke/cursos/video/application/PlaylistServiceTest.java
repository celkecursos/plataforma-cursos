package br.com.celke.cursos.video.application;

import static br.com.celke.cursos.support.fixtures.VideoFixture.PLAYLIST;
import static br.com.celke.cursos.support.fixtures.VideoFixture.aguardandoEnvio;
import static br.com.celke.cursos.support.fixtures.VideoFixture.pronto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.matricula.application.AcessoAulaService;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.erro.AcessoNegadoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.video.domain.Video;
import br.com.celke.cursos.video.infra.VideoRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

    private static final OrigemRequisicao ORIGEM = new OrigemRequisicao("203.0.113.9", "hls.js");
    private static final Duration MARGEM = Duration.ofHours(2);

    @Mock
    private AcessoAulaService acessoAulaService;

    @Mock
    private VideoRepository videos;

    @Mock
    private StorageService storage;

    @Mock
    private LogAcessoVideoService logAcessoVideoService;

    private PlaylistService service;

    @BeforeEach
    void criar() {
        var props = new VideoProperties(
                DataSize.ofGigabytes(5),
                Duration.ofHours(6),
                "ffmpeg",
                "ffprobe",
                null,
                "veryfast",
                24,
                Path.of("/tmp/videos"),
                MARGEM,
                Duration.ofDays(180));
        service = new PlaylistService(acessoAulaService, videos, storage, logAcessoVideoService, props);
    }

    private static byte[] bytes(String texto) {
        return texto.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("master: confere o acesso, reescreve as variantes e registra o acesso")
    void master() {
        when(videos.findByAulaId(9L)).thenReturn(Optional.of(pronto(3)));
        when(storage.baixar(PLAYLIST)).thenReturn(bytes("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\n720p/index.m3u8\n"));

        assertThat(service.master(7L, 9L, ORIGEM)).contains("video/720p.m3u8");
        verify(acessoAulaService).exigirAcesso(7L, 9L);
        verify(logAcessoVideoService).registrar(7L, 9L, ORIGEM);
    }

    @Test
    @DisplayName("variante: segmentos assinados pela duração do vídeo mais a margem")
    void variante() throws Exception {
        when(videos.findByAulaId(9L)).thenReturn(Optional.of(pronto(3)));
        when(storage.baixar("videos/9/1/720p/index.m3u8")).thenReturn(bytes("#EXTINF:3.0,\nseg_0000.ts\n"));
        when(storage.urlDownload(
                        "videos/9/1/720p/seg_0000.ts", Duration.ofSeconds(3).plus(MARGEM)))
                .thenReturn(URI.create("http://media.localhost/assinado").toURL());

        assertThat(service.variante(7L, 9L, "720p")).contains("http://media.localhost/assinado");
        verify(logAcessoVideoService, never()).registrar(any(), any(), any());
    }

    @Test
    @DisplayName("variante que o vídeo não tem responde 404 sem ler o bucket")
    void varianteInexistente() {
        when(videos.findByAulaId(9L)).thenReturn(Optional.of(pronto(3)));

        assertThatThrownBy(() -> service.variante(7L, 9L, "1080p"))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Variante não encontrada.");
        verify(storage, never()).baixar(anyString());
    }

    @Test
    @DisplayName("sem acesso à aula: a política responde antes de qualquer leitura, e nada vai para o log")
    void semAcesso() {
        doThrow(new AcessoNegadoException("Você não tem acesso a esta aula."))
                .when(acessoAulaService)
                .exigirAcesso(7L, 9L);

        assertThatThrownBy(() -> service.master(7L, 9L, ORIGEM)).isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.variante(7L, 9L, "720p")).isInstanceOf(AcessoNegadoException.class);
        verify(videos, never()).findByAulaId(any());
        verify(logAcessoVideoService, never()).registrar(any(), any(), any());
    }

    @Test
    @DisplayName("aula sem vídeo ou com vídeo ainda não publicado responde 404")
    void semVideoPublicado() {
        when(videos.findByAulaId(9L)).thenReturn(Optional.empty()).thenReturn(Optional.of(aguardandoEnvio(3)));

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> service.master(7L, 9L, ORIGEM))
                    .isInstanceOf(NaoEncontradoException.class)
                    .hasMessage("A aula não tem vídeo.");
        }
        verify(logAcessoVideoService, never()).registrar(any(), any(), any());
    }

    @Test
    @DisplayName("validade: duração + margem; sem duração conhecida, só a margem")
    void validade() {
        Video semDuracao = aguardandoEnvio(3);

        assertThat(service.validade(pronto(3))).isEqualTo(Duration.ofSeconds(3).plus(MARGEM));
        assertThat(service.validade(semDuracao)).isEqualTo(MARGEM);
    }
}
