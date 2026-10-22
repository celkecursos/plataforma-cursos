package br.com.celke.cursos.video.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VideoTest {

    private static final Instant T0 = Instant.parse("2026-03-10T12:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);

    private static Video aguardandoEnvio() {
        Video video = new Video(9L, T0);
        video.iniciarEnvio("uploads/videos/9/a", "upload-a", "aula.mp4", 1000, T0);
        return video;
    }

    private static Video pronto(long tamanhoOriginal, long tamanhoFinal, int duracao, long tempoMs) {
        Video video = new Video(9L, T0);
        video.iniciarEnvio("uploads/videos/9/a", "upload-a", "aula.mp4", tamanhoOriginal, T0);
        video.enfileirar(tamanhoOriginal, T0);
        video.iniciarProcessamento(T0);
        video.concluir(resultado("videos/9/1/master.m3u8", tamanhoFinal, duracao, tempoMs), T1);
        return video;
    }

    private static ResultadoTranscodificacao resultado(String playlist, long tamanhoFinal, int duracao, long tempoMs) {
        return new ResultadoTranscodificacao(
                playlist, "videos/9/1/thumbnail.jpg", tamanhoFinal, duracao, 1920, 1080, "720p,1080p", tempoMs);
    }

    @Test
    @DisplayName("vídeo novo aguarda o envio")
    void novo() {
        Video video = new Video(9L, T0);

        assertThat(video.getStatus()).isEqualTo(StatusVideo.AGUARDANDO_ENVIO);
        assertThat(video.getAulaId()).isEqualTo(9L);
        assertThat(video.getAtualizadoEm()).isEqualTo(T0);
    }

    @Nested
    class Ciclo {

        @Test
        @DisplayName("envio → fila → processamento → pronto: publica o HLS e esquece o original")
        void cicloCompleto() {
            Video video = aguardandoEnvio();
            assertThat(video.getUploadId()).isEqualTo("upload-a");

            video.enfileirar(1234, T0);
            assertThat(video.getStatus()).isEqualTo(StatusVideo.PENDENTE);
            assertThat(video.getUploadId()).isNull();
            assertThat(video.getTamanhoOriginal()).isEqualTo(1234);
            assertThat(video.getUploadChave()).isEqualTo("uploads/videos/9/a");

            video.iniciarProcessamento(T0);
            assertThat(video.getStatus()).isEqualTo(StatusVideo.PROCESSANDO);

            String anterior = video.concluir(resultado("videos/9/1/master.m3u8", 600, 30, 15_000), T1);
            assertThat(anterior).isNull();
            assertThat(video.getStatus()).isEqualTo(StatusVideo.PRONTO);
            assertThat(video.getPlaylistChave()).isEqualTo("videos/9/1/master.m3u8");
            assertThat(video.getThumbnailChave()).isEqualTo("videos/9/1/thumbnail.jpg");
            assertThat(video.getTamanhoFinal()).isEqualTo(600);
            assertThat(video.getDuracaoSegundos()).isEqualTo(30);
            assertThat(video.getLargura()).isEqualTo(1920);
            assertThat(video.getAltura()).isEqualTo(1080);
            assertThat(video.getVariantes()).isEqualTo("720p,1080p");
            assertThat(video.getTempoProcessamentoMs()).isEqualTo(15_000);
            assertThat(video.getProcessadoEm()).isEqualTo(T1);
            assertThat(video.getUploadChave()).isNull();
        }

        @Test
        @DisplayName("novo envio num vídeo pronto mantém o HLS publicado até a nova conversão, que devolve o anterior")
        void substituicao() {
            Video video = pronto(1000, 500, 60, 1000);

            video.iniciarEnvio("uploads/videos/9/b", "upload-b", "nova.mov", 2000, T1);
            assertThat(video.getStatus()).isEqualTo(StatusVideo.AGUARDANDO_ENVIO);
            assertThat(video.getPlaylistChave()).as("continua no ar").isEqualTo("videos/9/1/master.m3u8");
            assertThat(video.getNomeOriginal()).isEqualTo("nova.mov");

            video.enfileirar(2000, T1);
            video.iniciarProcessamento(T1);
            assertThat(video.concluir(resultado("videos/9/2/master.m3u8", 900, 60, 1000), T1))
                    .isEqualTo("videos/9/1/master.m3u8");
            assertThat(video.getPlaylistChave()).isEqualTo("videos/9/2/master.m3u8");
        }

        @Test
        @DisplayName("erro guarda a mensagem; reprocessar volta para a fila e limpa o erro")
        void erroEReprocessamento() {
            Video video = aguardandoEnvio();
            video.enfileirar(1000, T0);
            video.falhar("ffmpeg falhou", T1);
            assertThat(video.getStatus()).isEqualTo(StatusVideo.ERRO);
            assertThat(video.getErro()).isEqualTo("ffmpeg falhou");
            assertThat(video.getUploadChave())
                    .as("original guardado para reprocessar")
                    .isNotNull();

            video.reprocessar(T1);
            assertThat(video.getStatus()).isEqualTo(StatusVideo.PENDENTE);
            assertThat(video.getErro()).isNull();
        }

        @Test
        @DisplayName("envio novo depois de um erro limpa a mensagem")
        void envioDepoisDoErro() {
            Video video = aguardandoEnvio();
            video.falhar("recusado", T0);

            video.iniciarEnvio("uploads/videos/9/c", "upload-c", "c.mp4", 10, T1);

            assertThat(video.getErro()).isNull();
            assertThat(video.getStatus()).isEqualTo(StatusVideo.AGUARDANDO_ENVIO);
        }

        @Test
        @DisplayName("descartar o original apaga a referência ao upload")
        void descartarOriginal() {
            Video video = aguardandoEnvio();

            video.descartarOriginal(T1);

            assertThat(video.getUploadChave()).isNull();
            assertThat(video.getUploadId()).isNull();
        }

        @Test
        @DisplayName("mensagem de erro longa guarda só o fim (onde o ffmpeg diz o motivo)")
        void erroLongo() {
            Video video = aguardandoEnvio();
            String longa = "a".repeat(Video.TAMANHO_ERRO) + "motivo";

            video.falhar(longa, T0);

            assertThat(video.getErro()).hasSize(Video.TAMANHO_ERRO).endsWith("motivo");
        }
    }

    @Nested
    class TransicoesProibidas {

        @Test
        @DisplayName("não inicia envio com o vídeo na fila ou com o worker")
        void envioDuranteProcessamento() {
            Video video = aguardandoEnvio();
            video.enfileirar(1000, T0);

            assertThatThrownBy(() -> video.iniciarEnvio("x", "y", "z.mp4", 1, T1))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("O vídeo está em processamento.");
            video.iniciarProcessamento(T0);
            assertThatThrownBy(video::exigirForaDoProcessamento).isInstanceOf(IllegalStateException.class);
            assertThat(video.getUploadChave()).isEqualTo("uploads/videos/9/a");
        }

        @Test
        @DisplayName("só um envio em andamento vai para a fila")
        void enfileirarForaDoEnvio() {
            Video video = pronto(1000, 500, 60, 1000);

            assertThatThrownBy(() -> video.enfileirar(10, T1))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Só um envio em andamento pode ir para a fila.");
            assertThat(video.getStatus()).isEqualTo(StatusVideo.PRONTO);
        }

        @Test
        @DisplayName("só vídeo com erro é reprocessado")
        void reprocessarSemErro() {
            Video video = pronto(1000, 500, 60, 1000);

            assertThatThrownBy(() -> video.reprocessar(T1))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Só um vídeo com erro pode ser reprocessado.");
            assertThat(video.getStatus()).isEqualTo(StatusVideo.PRONTO);
        }
    }

    @Nested
    class Medidas {

        @ParameterizedTest(name = "{0} → {1} bytes: {2}%")
        @CsvSource({"1000, 250, 75.0", "1000, 1000, 0.0", "1000, 1500, -50.0", "3, 1, 66.7", "3, 2, 33.3"})
        @DisplayName("redução do original para o HLS, com uma casa decimal")
        void reducao(long original, long fim, double esperado) {
            assertThat(pronto(original, fim, 60, 1000).reducaoPercentual()).isEqualTo(esperado);
        }

        @Test
        @DisplayName("sem conversão concluída não há redução")
        void semReducao() {
            assertThat(aguardandoEnvio().reducaoPercentual()).isNull();
        }

        @ParameterizedTest(name = "{1} ms para {0} s → {2} s/min")
        @CsvSource({"60, 30000, 30.0", "120, 30000, 15.0", "3, 1500, 30.0", "90, 1000, 0.7"})
        @DisplayName("segundos de processamento por minuto de vídeo")
        void tempoPorMinuto(int duracao, long tempoMs, double esperado) {
            assertThat(pronto(1000, 500, duracao, tempoMs).segundosPorMinutoDeVideo())
                    .isEqualTo(esperado);
        }

        @Test
        @DisplayName("sem duração (ou zero) não há medida de tempo")
        void semTempo() {
            assertThat(aguardandoEnvio().segundosPorMinutoDeVideo()).isNull();
            assertThat(pronto(1000, 500, 0, 1000).segundosPorMinutoDeVideo()).isNull();
        }
    }

    @Test
    @DisplayName("só PENDENTE e PROCESSANDO contam como em processamento")
    void emProcessamento() {
        assertThat(StatusVideo.values())
                .filteredOn(StatusVideo::emProcessamento)
                .containsExactly(StatusVideo.PENDENTE, StatusVideo.PROCESSANDO);
    }
}
