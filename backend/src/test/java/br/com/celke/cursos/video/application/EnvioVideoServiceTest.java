package br.com.celke.cursos.video.application;

import static br.com.celke.cursos.support.fixtures.VideoFixture.UPLOAD;
import static br.com.celke.cursos.support.fixtures.VideoFixture.aguardandoEnvio;
import static br.com.celke.cursos.support.fixtures.VideoFixture.comErro;
import static br.com.celke.cursos.support.fixtures.VideoFixture.pronto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.armazenamento.StorageService.InfoArquivo;
import br.com.celke.cursos.shared.armazenamento.StorageService.ParteEnviada;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.video.application.VideoAdmService.EnvioAnterior;
import br.com.celke.cursos.video.application.VideoAdmService.EnvioRegistrado;
import br.com.celke.cursos.video.domain.Video;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class EnvioVideoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final Duration VALIDADE = Duration.ofHours(6);
    private static final byte[] MP4 = HexFormat.of().parseHex("000000206674797069736f6d");
    private static final List<ParteEnviada> PARTES = List.of(new ParteEnviada(1, "e1"));

    @Mock
    private VideoAdmService videoAdmService;

    @Mock
    private StorageService storage;

    private EnvioVideoService service;

    @BeforeEach
    void criar() {
        var props = new VideoProperties(
                DataSize.ofGigabytes(5), VALIDADE, "ffmpeg", "ffprobe", null, "veryfast", 24, Path.of("/tmp/videos"));
        service = new EnvioVideoService(videoAdmService, storage, props, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static URL url(String caminho) throws Exception {
        return URI.create("http://media.localhost/" + caminho).toURL();
    }

    @Nested
    class Iniciar {

        @Test
        @DisplayName("abre o upload em partes, registra o envio e devolve uma URL por parte")
        void inicia() throws Exception {
            Video video = aguardandoEnvio(3);
            when(storage.iniciarMultipart(startsWith("uploads/videos/9/"), eq("video/mp4")))
                    .thenReturn("upload-x");
            when(videoAdmService.registrarEnvio(
                            eq(9L),
                            startsWith("uploads/videos/9/"),
                            eq("upload-x"),
                            eq("aula.mp4"),
                            eq(40L * 1024 * 1024)))
                    .thenReturn(new EnvioRegistrado(video, Optional.empty()));
            when(storage.urlParte(startsWith("uploads/videos/9/"), eq("upload-x"), anyInt(), eq(VALIDADE)))
                    .thenAnswer(i -> url("parte-" + i.getArgument(2)));

            var envio = service.iniciar(9L, " aula.mp4 ", "video/mp4", 40L * 1024 * 1024);

            verify(videoAdmService).exigirEnvioPermitido(9L);
            assertThat(envio.tamanhoParte()).isEqualTo(16L * 1024 * 1024);
            assertThat(envio.partes())
                    .extracting(EnvioVideoService.UrlParte::numero)
                    .containsExactly(1, 2, 3);
            assertThat(envio.partes().getLast().url().toString()).endsWith("parte-3");
            assertThat(envio.expiraEm()).isEqualTo(AGORA.plus(VALIDADE));
            assertThat(envio.video()).isSameAs(video);
            verify(storage, never()).abortarMultipart(anyString(), anyString());
        }

        @Test
        @DisplayName("envio anterior ainda aberto é abortado; falha ao abortar não atrapalha")
        void abortaAnterior() throws Exception {
            when(storage.iniciarMultipart(anyString(), eq("video/webm"))).thenReturn("upload-x");
            when(videoAdmService.registrarEnvio(eq(9L), anyString(), eq("upload-x"), eq("a.webm"), eq(10L)))
                    .thenReturn(new EnvioRegistrado(
                            aguardandoEnvio(3), Optional.of(new EnvioAnterior("velha", "upload-velho"))));
            doThrow(S3Exception.builder().message("NoSuchUpload").build())
                    .when(storage)
                    .abortarMultipart("velha", "upload-velho");
            when(storage.urlParte(anyString(), eq("upload-x"), eq(1), eq(VALIDADE)))
                    .thenReturn(url("p1"));

            assertThat(service.iniciar(9L, "a.webm", "video/webm", 10).partes()).hasSize(1);
            verify(storage).abortarMultipart("velha", "upload-velho");
        }

        @Test
        @DisplayName("tipo fora da lista é recusado antes de qualquer coisa")
        void tipoRecusado() {
            assertThatThrownBy(() -> service.iniciar(9L, "a.avi", "video/x-msvideo", 10))
                    .isInstanceOf(VideoInvalidoException.class)
                    .hasMessage("Envie um vídeo MP4, MOV, MKV ou WebM.");
            verify(storage, never()).iniciarMultipart(anyString(), anyString());
            verify(videoAdmService, never()).exigirEnvioPermitido(any());
        }

        @Test
        @DisplayName("acima do tamanho máximo é recusado; exatamente no limite passa")
        void tamanhoMaximo() throws Exception {
            long limite = DataSize.ofGigabytes(5).toBytes();
            assertThatThrownBy(() -> service.iniciar(9L, "a.mp4", "video/mp4", limite + 1))
                    .isInstanceOf(VideoInvalidoException.class)
                    .hasMessage("O vídeo pode ter no máximo 5 GB.");
            verify(storage, never()).iniciarMultipart(anyString(), anyString());

            when(storage.iniciarMultipart(anyString(), anyString())).thenReturn("u");
            when(videoAdmService.registrarEnvio(eq(9L), anyString(), eq("u"), eq("a.mp4"), eq(limite)))
                    .thenReturn(new EnvioRegistrado(aguardandoEnvio(3), Optional.empty()));
            when(storage.urlParte(anyString(), eq("u"), anyInt(), eq(VALIDADE))).thenReturn(url("p"));
            assertThat(service.iniciar(9L, "a.mp4", "video/mp4", limite).partes())
                    .hasSize(320);
        }

        @Test
        @DisplayName("aula inexistente ou vídeo em processamento: nenhum upload é aberto")
        void bloqueado() {
            doThrow(VideoAdmService.emProcessamento()).when(videoAdmService).exigirEnvioPermitido(9L);

            assertThatThrownBy(() -> service.iniciar(9L, "a.mp4", "video/mp4", 10))
                    .isInstanceOf(ConflitoException.class);
            verify(storage, never()).iniciarMultipart(anyString(), anyString());
        }

        @Test
        @DisplayName("registro recusado (corrida) aborta o upload recém-aberto")
        void registroRecusado() {
            when(storage.iniciarMultipart(anyString(), anyString())).thenReturn("upload-x");
            when(videoAdmService.registrarEnvio(eq(9L), anyString(), eq("upload-x"), anyString(), anyLong()))
                    .thenThrow(VideoAdmService.emProcessamento());

            assertThatThrownBy(() -> service.iniciar(9L, "a.mp4", "video/mp4", 10))
                    .isInstanceOf(ConflitoException.class);
            verify(storage).abortarMultipart(startsWith("uploads/videos/9/"), eq("upload-x"));
        }
    }

    @Nested
    class Concluir {

        @Test
        @DisplayName("junta as partes, confere tamanho e assinatura e coloca na fila")
        void conclui() {
            Video video = aguardandoEnvio(3);
            Video naFila = aguardandoEnvio(3);
            when(videoAdmService.envioEmAndamento(9L)).thenReturn(video);
            when(storage.info(UPLOAD)).thenReturn(Optional.of(new InfoArquivo(1000, "video/mp4")));
            when(storage.baixarInicio(UPLOAD, 12)).thenReturn(MP4);
            when(videoAdmService.enfileirar(3L, 1000)).thenReturn(naFila);

            assertThat(service.concluir(9L, PARTES)).isSameAs(naFila);
            verify(storage).concluirMultipart(UPLOAD, "upload-1", PARTES);
            verify(storage, never()).excluir(anyString());
        }

        @Test
        @DisplayName("parte repetida é recusada antes de juntar")
        void parteRepetida() {
            when(videoAdmService.envioEmAndamento(9L)).thenReturn(aguardandoEnvio(3));

            assertThatThrownBy(() -> service.concluir(9L, List.of(new ParteEnviada(1, "a"), new ParteEnviada(1, "b"))))
                    .isInstanceOf(VideoInvalidoException.class)
                    .hasMessage("Cada parte pode ser informada uma vez só.");
            verify(storage, never()).concluirMultipart(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("falha ao juntar (parte faltando ou ETag errado) mantém o envio para nova tentativa")
        void falhaAoJuntar() {
            when(videoAdmService.envioEmAndamento(9L)).thenReturn(aguardandoEnvio(3));
            doThrow(S3Exception.builder().message("InvalidPart").build())
                    .when(storage)
                    .concluirMultipart(UPLOAD, "upload-1", PARTES);

            assertThatThrownBy(() -> service.concluir(9L, PARTES))
                    .isInstanceOf(VideoInvalidoException.class)
                    .hasMessage("O envio não foi concluído. Confira se todas as partes foram enviadas.");
            verify(videoAdmService, never()).recusarEnvio(any(), anyString());
            verify(videoAdmService, never()).enfileirar(any(), anyLong());
        }

        @Test
        @DisplayName("arquivo que sumiu depois de juntado")
        void arquivoSumiu() {
            when(videoAdmService.envioEmAndamento(9L)).thenReturn(aguardandoEnvio(3));
            when(storage.info(UPLOAD)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.concluir(9L, PARTES))
                    .isInstanceOf(VideoInvalidoException.class)
                    .hasMessage("O vídeo não foi enviado. Comece um novo envio.");
            verify(videoAdmService, never()).enfileirar(any(), anyLong());
        }

        @Test
        @DisplayName("tamanho real diferente do informado: apaga o arquivo e deixa o vídeo com erro")
        void tamanhoDiferente() {
            when(videoAdmService.envioEmAndamento(9L)).thenReturn(aguardandoEnvio(3));
            when(storage.info(UPLOAD)).thenReturn(Optional.of(new InfoArquivo(1001, "video/mp4")));

            String motivo = "O tamanho do arquivo enviado não confere com o informado. Comece um novo envio.";
            assertThatThrownBy(() -> service.concluir(9L, PARTES))
                    .isInstanceOf(VideoInvalidoException.class)
                    .hasMessage(motivo);
            verify(storage).excluir(UPLOAD);
            verify(videoAdmService).recusarEnvio(3L, motivo);
            verify(videoAdmService, never()).enfileirar(any(), anyLong());
        }

        @Test
        @DisplayName("conteúdo sem a assinatura do formato (extensão falsa) é apagado e recusado")
        void assinaturaFalsa() {
            when(videoAdmService.envioEmAndamento(9L)).thenReturn(aguardandoEnvio(3));
            when(storage.info(UPLOAD)).thenReturn(Optional.of(new InfoArquivo(1000, "video/mp4")));
            when(storage.baixarInicio(UPLOAD, 12)).thenReturn("<html><body>".getBytes());

            assertThatThrownBy(() -> service.concluir(9L, PARTES))
                    .isInstanceOf(VideoInvalidoException.class)
                    .hasMessage("O arquivo não é um vídeo MP4, MOV, MKV ou WebM.");
            verify(storage).excluir(UPLOAD);
            verify(videoAdmService).recusarEnvio(3L, "O arquivo não é um vídeo MP4, MOV, MKV ou WebM.");
            verify(videoAdmService, never()).enfileirar(any(), anyLong());
        }

        @Test
        @DisplayName("tipo gravado no bucket fora da lista também é recusado")
        void tipoGravadoInvalido() {
            when(videoAdmService.envioEmAndamento(9L)).thenReturn(aguardandoEnvio(3));
            when(storage.info(UPLOAD)).thenReturn(Optional.of(new InfoArquivo(1000, "text/html")));

            assertThatThrownBy(() -> service.concluir(9L, PARTES)).isInstanceOf(VideoInvalidoException.class);
            verify(storage).excluir(UPLOAD);
            verify(storage, never()).baixarInicio(anyString(), anyInt());
        }
    }

    @Nested
    class Reprocessar {

        @Test
        @DisplayName("com o original no bucket, volta para a fila")
        void reprocessa() {
            Video video = comErro(3);
            when(videoAdmService.buscarPorAula(9L)).thenReturn(video);
            when(storage.info(UPLOAD)).thenReturn(Optional.of(new InfoArquivo(1000, "video/mp4")));
            when(videoAdmService.reprocessar(9L)).thenReturn(video);

            assertThat(service.reprocessar(9L)).isSameAs(video);
        }

        @Test
        @DisplayName("original que expirou no bucket: esquece a referência e pede um envio novo")
        void originalExpirou() {
            when(videoAdmService.buscarPorAula(9L)).thenReturn(comErro(3));
            when(storage.info(UPLOAD)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.reprocessar(9L))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("O arquivo original não está mais disponível. Envie o vídeo de novo.");
            verify(videoAdmService).descartarOriginal(3L);
            verify(videoAdmService, never()).reprocessar(any());
        }

        @Test
        @DisplayName("sem original (já apagado) a regra fica com o VideoAdmService")
        void semOriginal() {
            Video video = pronto(3);
            when(videoAdmService.buscarPorAula(9L)).thenReturn(video);
            when(videoAdmService.reprocessar(9L))
                    .thenThrow(new ConflitoException("Só um vídeo com erro pode ser reprocessado."));

            assertThatThrownBy(() -> service.reprocessar(9L)).isInstanceOf(ConflitoException.class);
            verify(storage, never()).info(anyString());
        }
    }

    @Nested
    class Excluir {

        @Test
        @DisplayName("vídeo pronto: apaga o HLS inteiro pelo prefixo")
        void videoPronto() {
            when(videoAdmService.excluir(9L)).thenReturn(pronto(3));

            service.excluir(9L);

            verify(storage).excluirPrefixo("videos/9/1/");
            verify(storage, never()).excluir(anyString());
            verify(storage, never()).abortarMultipart(anyString(), anyString());
        }

        @Test
        @DisplayName("envio aberto é abortado; original com erro é apagado")
        void envioEOriginal() {
            when(videoAdmService.excluir(9L)).thenReturn(aguardandoEnvio(3)).thenReturn(comErro(3));

            service.excluir(9L);
            service.excluir(9L);

            verify(storage).abortarMultipart(UPLOAD, "upload-1");
            verify(storage).excluir(UPLOAD);
            verify(storage, never()).excluirPrefixo(anyString());
        }
    }

    @Test
    @DisplayName("thumbnail: URL temporária só com vídeo convertido")
    void thumbnail() throws Exception {
        when(storage.urlDownload("videos/9/1/thumbnail.jpg", Duration.ofHours(1)))
                .thenReturn(url("thumb"));

        assertThat(service.urlThumbnail(pronto(3))).contains(url("thumb"));
        assertThat(service.urlThumbnail(aguardandoEnvio(3))).isEmpty();
    }

    @Test
    @DisplayName("tamanho máximo legível em GB ou MB")
    void legivel() {
        assertThat(EnvioVideoService.legivel(DataSize.ofGigabytes(5))).isEqualTo("5 GB");
        assertThat(EnvioVideoService.legivel(DataSize.ofMegabytes(500))).isEqualTo("500 MB");
        assertThat(EnvioVideoService.prefixoHls("videos/9/1/master.m3u8")).isEqualTo("videos/9/1/");
    }
}
