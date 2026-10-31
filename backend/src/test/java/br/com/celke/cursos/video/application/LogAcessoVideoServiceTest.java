package br.com.celke.cursos.video.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.video.domain.AcessoVideo;
import br.com.celke.cursos.video.infra.AcessoVideoRepository;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class LogAcessoVideoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-07-01T12:00:00Z");

    @Mock
    private AcessoVideoRepository acessos;

    private LogAcessoVideoService service;

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
                Duration.ofHours(2),
                Duration.ofDays(180));
        service = new LogAcessoVideoService(acessos, props, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("registra usuário, aula, IP, navegador e o instante")
    void registra() {
        service.registrar(7L, 9L, new OrigemRequisicao("203.0.113.9", "hls.js"));

        ArgumentCaptor<AcessoVideo> captor = ArgumentCaptor.forClass(AcessoVideo.class);
        verify(acessos).save(captor.capture());
        AcessoVideo acesso = captor.getValue();
        assertThat(acesso.getUsuarioId()).isEqualTo(7L);
        assertThat(acesso.getAulaId()).isEqualTo(9L);
        assertThat(acesso.getIp()).isEqualTo("203.0.113.9");
        assertThat(acesso.getUserAgent()).isEqualTo("hls.js");
        assertThat(acesso.getAcessadoEm()).isEqualTo(AGORA);
    }

    @Test
    @DisplayName("apaga o que passou da retenção (180 dias antes de agora)")
    void apagaVencidos() {
        when(acessos.apagarAnteriores(AGORA.minus(Duration.ofDays(180)))).thenReturn(4);

        assertThat(service.apagarVencidos()).isEqualTo(4);
    }
}
