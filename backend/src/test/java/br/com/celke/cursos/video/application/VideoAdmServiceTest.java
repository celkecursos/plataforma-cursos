package br.com.celke.cursos.video.application;

import static br.com.celke.cursos.support.fixtures.VideoFixture.PLAYLIST;
import static br.com.celke.cursos.support.fixtures.VideoFixture.UPLOAD;
import static br.com.celke.cursos.support.fixtures.VideoFixture.aguardandoEnvio;
import static br.com.celke.cursos.support.fixtures.VideoFixture.comErro;
import static br.com.celke.cursos.support.fixtures.VideoFixture.pendente;
import static br.com.celke.cursos.support.fixtures.VideoFixture.pronto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.fila.FilaService;
import br.com.celke.cursos.video.domain.ResultadoTranscodificacao;
import br.com.celke.cursos.video.domain.StatusVideo;
import br.com.celke.cursos.video.domain.Video;
import br.com.celke.cursos.video.infra.VideoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class VideoAdmServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final String TIPO = "video.transcodificar";

    @Mock
    private VideoRepository videos;

    @Mock
    private ConteudoTurmaService conteudoTurmaService;

    @Mock
    private FilaService fila;

    private VideoAdmService service;

    @BeforeEach
    void criar() {
        service = new VideoAdmService(videos, conteudoTurmaService, fila, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private void aulaExiste() {
        when(conteudoTurmaService.aulaExiste(9L)).thenReturn(true);
    }

    @Nested
    class Buscar {

        @Test
        @DisplayName("aula inexistente responde aula não encontrada")
        void aulaInexistente() {
            when(conteudoTurmaService.aulaExiste(9L)).thenReturn(false);

            assertThatThrownBy(() -> service.buscarPorAula(9L))
                    .isInstanceOf(NaoEncontradoException.class)
                    .hasMessage("Aula não encontrada.");
        }

        @Test
        @DisplayName("aula sem vídeo responde a aula não tem vídeo")
        void semVideo() {
            aulaExiste();
            when(videos.findByAulaId(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.buscarPorAula(9L))
                    .isInstanceOf(NaoEncontradoException.class)
                    .hasMessage("A aula não tem vídeo.");
        }

        @Test
        @DisplayName("envio é permitido sem vídeo ou com vídeo fora do processamento, recusado durante")
        void envioPermitido() {
            aulaExiste();
            when(videos.findByAulaId(9L))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(pronto(3)))
                    .thenReturn(Optional.of(pendente(3)));

            service.exigirEnvioPermitido(9L);
            service.exigirEnvioPermitido(9L);
            assertThatThrownBy(() -> service.exigirEnvioPermitido(9L))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("O vídeo da aula está em processamento. Aguarde o fim para alterá-lo.");
        }
    }

    @Nested
    class RegistrarEnvio {

        @Test
        @DisplayName("primeiro envio cria o vídeo da aula, sem envio anterior")
        void primeiro() {
            aulaExiste();
            when(videos.travarPorAula(9L)).thenReturn(Optional.empty());
            when(videos.saveAndFlush(any(Video.class))).thenAnswer(i -> i.getArgument(0));

            var registro = service.registrarEnvio(9L, "uploads/videos/9/x", "u-x", "a.mp4", 500);

            assertThat(registro.anterior()).isEmpty();
            Video video = registro.video();
            assertThat(video.getAulaId()).isEqualTo(9L);
            assertThat(video.getStatus()).isEqualTo(StatusVideo.AGUARDANDO_ENVIO);
            assertThat(video.getUploadChave()).isEqualTo("uploads/videos/9/x");
            assertThat(video.getUploadId()).isEqualTo("u-x");
            assertThat(video.getTamanhoOriginal()).isEqualTo(500);
        }

        @Test
        @DisplayName("envio aberto anterior é devolvido para ser abortado")
        void comEnvioAnterior() {
            aulaExiste();
            when(videos.travarPorAula(9L)).thenReturn(Optional.of(aguardandoEnvio(3)));
            when(videos.saveAndFlush(any(Video.class))).thenAnswer(i -> i.getArgument(0));

            var registro = service.registrarEnvio(9L, "uploads/videos/9/y", "u-y", "b.mp4", 10);

            assertThat(registro.anterior()).contains(new VideoAdmService.EnvioAnterior(UPLOAD, "upload-1"));
            assertThat(registro.video().getUploadId()).isEqualTo("u-y");
        }

        @Test
        @DisplayName("vídeo pronto recebe envio novo sem envio anterior a abortar")
        void substituiPronto() {
            aulaExiste();
            when(videos.travarPorAula(9L)).thenReturn(Optional.of(pronto(3)));
            when(videos.saveAndFlush(any(Video.class))).thenAnswer(i -> i.getArgument(0));

            var registro = service.registrarEnvio(9L, "uploads/videos/9/y", "u-y", "b.mp4", 10);

            assertThat(registro.anterior()).isEmpty();
            assertThat(registro.video().getPlaylistChave()).isEqualTo(PLAYLIST);
        }

        @Test
        @DisplayName("vídeo em processamento recusa envio novo")
        void emProcessamento() {
            aulaExiste();
            when(videos.travarPorAula(9L)).thenReturn(Optional.of(pendente(3)));

            assertThatThrownBy(() -> service.registrarEnvio(9L, "k", "u", "a.mp4", 10))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("O vídeo da aula está em processamento. Aguarde o fim para alterá-lo.");
            verify(videos, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("dois primeiros envios ao mesmo tempo: a restrição única recusa o segundo com 409")
        void corrida() {
            aulaExiste();
            when(videos.travarPorAula(9L)).thenReturn(Optional.empty());
            when(videos.saveAndFlush(any(Video.class)))
                    .thenThrow(new DataIntegrityViolationException("uq_videos_aula"));

            assertThatThrownBy(() -> service.registrarEnvio(9L, "k", "u", "a.mp4", 10))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("Outro envio para esta aula começou ao mesmo tempo. Tente de novo.");
        }

        @Test
        @DisplayName("aula inexistente não registra nada")
        void aulaInexistente() {
            when(conteudoTurmaService.aulaExiste(9L)).thenReturn(false);

            assertThatThrownBy(() -> service.registrarEnvio(9L, "k", "u", "a.mp4", 10))
                    .isInstanceOf(NaoEncontradoException.class);
            verify(videos, never()).saveAndFlush(any());
        }
    }

    @Nested
    class Conclusao {

        @Test
        @DisplayName("envio em andamento é devolvido; sem envio, 409; aula inexistente, 404")
        void envioEmAndamento() {
            Video aberto = aguardandoEnvio(3);
            when(videos.findByAulaId(9L))
                    .thenReturn(Optional.of(aberto))
                    .thenReturn(Optional.of(pronto(3)))
                    .thenReturn(Optional.empty());
            when(conteudoTurmaService.aulaExiste(9L)).thenReturn(true).thenReturn(false);

            assertThat(service.envioEmAndamento(9L)).isSameAs(aberto);
            assertThatThrownBy(() -> service.envioEmAndamento(9L))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("Nenhum envio em andamento para esta aula. Comece um novo envio.");
            assertThatThrownBy(() -> service.envioEmAndamento(9L))
                    .isInstanceOf(NaoEncontradoException.class)
                    .hasMessage("Aula não encontrada.");
        }

        @Test
        @DisplayName("enfileirar grava o tamanho real e coloca o job com o vídeo e a chave do original")
        void enfileirar() {
            when(videos.travar(3L)).thenReturn(Optional.of(aguardandoEnvio(3)));

            Video video = service.enfileirar(3L, 999);

            assertThat(video.getStatus()).isEqualTo(StatusVideo.PENDENTE);
            assertThat(video.getTamanhoOriginal()).isEqualTo(999);
            verify(fila).enfileirar(TIPO, new PayloadTranscodificacao(3L, UPLOAD), AGORA);
        }

        @Test
        @DisplayName("enfileirar fora de um envio em andamento é recusado sem criar job")
        void enfileirarForaDoEnvio() {
            when(videos.travar(3L)).thenReturn(Optional.of(pendente(3)));

            assertThatThrownBy(() -> service.enfileirar(3L, 999)).isInstanceOf(ConflitoException.class);
            verify(fila, never()).enfileirar(anyString(), any(), any());
        }

        @Test
        @DisplayName("envio recusado: vídeo com erro e sem original")
        void recusar() {
            Video video = aguardandoEnvio(3);
            when(videos.travar(3L)).thenReturn(Optional.of(video));

            service.recusarEnvio(3L, "O arquivo não é um vídeo.");

            assertThat(video.getStatus()).isEqualTo(StatusVideo.ERRO);
            assertThat(video.getErro()).isEqualTo("O arquivo não é um vídeo.");
            assertThat(video.getUploadChave()).isNull();
        }

        @Test
        @DisplayName("vídeo removido no meio responde a aula não tem vídeo")
        void videoSumiu() {
            when(videos.travar(3L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.enfileirar(3L, 1)).isInstanceOf(NaoEncontradoException.class);
        }
    }

    @Nested
    class Reprocessar {

        @Test
        @DisplayName("vídeo com erro e original guardado volta para a fila")
        void reprocessa() {
            aulaExiste();
            when(videos.travarPorAula(9L)).thenReturn(Optional.of(comErro(3)));

            assertThat(service.reprocessar(9L).getStatus()).isEqualTo(StatusVideo.PENDENTE);
            verify(fila).enfileirar(TIPO, new PayloadTranscodificacao(3L, UPLOAD), AGORA);
        }

        @Test
        @DisplayName("só vídeo com erro")
        void semErro() {
            aulaExiste();
            when(videos.travarPorAula(9L)).thenReturn(Optional.of(pronto(3)));

            assertThatThrownBy(() -> service.reprocessar(9L))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("Só um vídeo com erro pode ser reprocessado.");
            verify(fila, never()).enfileirar(anyString(), any(), any());
        }

        @Test
        @DisplayName("sem o original, pede um envio novo")
        void semOriginal() {
            Video video = comErro(3);
            video.descartarOriginal(AGORA);
            aulaExiste();
            when(videos.travarPorAula(9L)).thenReturn(Optional.of(video));

            assertThatThrownBy(() -> service.reprocessar(9L))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("O arquivo original não está mais disponível. Envie o vídeo de novo.");
            verify(fila, never()).enfileirar(anyString(), any(), any());
        }

        @Test
        @DisplayName("descartar o original esquece a chave")
        void descartar() {
            Video video = comErro(3);
            when(videos.travar(3L)).thenReturn(Optional.of(video));

            service.descartarOriginal(3L);

            assertThat(video.getUploadChave()).isNull();
        }
    }

    @Nested
    class Excluir {

        @Test
        @DisplayName("exclui o vídeo fora do processamento e devolve o que apagar")
        void exclui() {
            Video video = pronto(3);
            aulaExiste();
            when(videos.travarPorAula(9L)).thenReturn(Optional.of(video));

            assertThat(service.excluir(9L)).isSameAs(video);
            verify(videos).delete(video);
        }

        @Test
        @DisplayName("vídeo em processamento não é excluído")
        void emProcessamento() {
            aulaExiste();
            when(videos.travarPorAula(9L)).thenReturn(Optional.of(pendente(3)));

            assertThatThrownBy(() -> service.excluir(9L)).isInstanceOf(ConflitoException.class);
            verify(videos, never()).delete(any());
        }

        @Test
        @DisplayName("aula sem vídeo responde 404")
        void semVideo() {
            aulaExiste();
            when(videos.travarPorAula(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.excluir(9L))
                    .isInstanceOf(NaoEncontradoException.class)
                    .hasMessage("A aula não tem vídeo.");
        }
    }

    @Nested
    class Worker {

        private final PayloadTranscodificacao payload = new PayloadTranscodificacao(3L, UPLOAD);

        @Test
        @DisplayName("inicia o processamento do envio do job")
        void inicia() {
            when(videos.travar(3L)).thenReturn(Optional.of(pendente(3)));

            assertThat(service.iniciarProcessamento(payload))
                    .hasValueSatisfying(v -> assertThat(v.getStatus()).isEqualTo(StatusVideo.PROCESSANDO));
        }

        @Test
        @DisplayName("job velho (outro envio, vídeo pronto, com erro ou excluído) não processa")
        void jobVelho() {
            Video outroEnvio = pendente(3);
            outroEnvio.falhar("x", AGORA);
            outroEnvio.iniciarEnvio("uploads/videos/9/outro", "u2", "b.mp4", 5, AGORA);
            outroEnvio.enfileirar(5, AGORA);
            when(videos.travar(3L))
                    .thenReturn(Optional.of(outroEnvio))
                    .thenReturn(Optional.of(pronto(3)))
                    .thenReturn(Optional.of(comErro(3)))
                    .thenReturn(Optional.empty());

            for (int i = 0; i < 4; i++) {
                assertThat(service.iniciarProcessamento(payload)).isEmpty();
            }
        }

        @Test
        @DisplayName("concluir publica e devolve a playlist anterior")
        void concluir() {
            Video video = pronto(3);
            video.iniciarEnvio("uploads/videos/9/novo", "u", "n.mp4", 10, AGORA);
            video.enfileirar(10, AGORA);
            video.iniciarProcessamento(AGORA);
            when(videos.travar(3L)).thenReturn(Optional.of(video));

            var anterior = service.concluir(
                    3L, new ResultadoTranscodificacao("videos/9/2/master.m3u8", "t", 1, 1, 1, 1, "720p", 1));

            assertThat(anterior).contains(PLAYLIST);
            assertThat(video.getPlaylistChave()).isEqualTo("videos/9/2/master.m3u8");
        }

        @Test
        @DisplayName("desistência deixa com erro só o vídeo do mesmo envio")
        void falhar() {
            Video doJob = pendente(3);
            Video pronto = pronto(3);
            when(videos.travar(3L)).thenReturn(Optional.of(doJob)).thenReturn(Optional.of(pronto));

            service.falharProcessamento(payload, "ffmpeg falhou");
            service.falharProcessamento(payload, "ffmpeg falhou");

            assertThat(doJob.getStatus()).isEqualTo(StatusVideo.ERRO);
            assertThat(doJob.getErro()).isEqualTo("ffmpeg falhou");
            assertThat(pronto.getStatus()).isEqualTo(StatusVideo.PRONTO);
        }
    }
}
