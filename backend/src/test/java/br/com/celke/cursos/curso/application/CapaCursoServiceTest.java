package br.com.celke.cursos.curso.application;

import static br.com.celke.cursos.support.fixtures.CursoFixture.comCapa;
import static br.com.celke.cursos.support.fixtures.CursoFixture.curso;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.armazenamento.StorageService.InfoArquivo;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.imagem.ImagemInvalidaException;
import br.com.celke.cursos.shared.imagem.ProcessadorImagem;
import java.net.URI;
import java.net.URL;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CapaCursoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final String CHAVE = "uploads/capas/1/0b1e8f6a-4c2d-4e8b-9a51-7f3c2d1e0a9b";
    private static final byte[] ORIGINAL = {1, 2, 3};
    private static final byte[] PROCESSADA = {4, 5};

    @Mock
    private CursoAdmService cursoAdmService;

    @Mock
    private StorageService storage;

    @Mock
    private ProcessadorImagem processador;

    private CapaCursoService service;

    @BeforeEach
    void setUp() {
        service = new CapaCursoService(cursoAdmService, storage, processador, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static URL url(String endereco) throws Exception {
        return URI.create(endereco).toURL();
    }

    @Nested
    class IniciarEnvio {

        @ParameterizedTest(name = "{0}")
        @CsvSource({"image/jpeg, 1", "image/png, 5242880"})
        void devolveUrlComTipoEValidade(String tipo, long tamanho) throws Exception {
            when(storage.urlUpload(startsWith("uploads/capas/1/"), eq(tipo), eq(CapaCursoService.VALIDADE_UPLOAD)))
                    .thenReturn(url("http://s3/assinada"));

            var envio = service.iniciarEnvio(1L, tipo, tamanho);

            assertThat(envio.chave()).matches("uploads/capas/1/[0-9a-f-]{36}");
            assertThat(envio.contentType()).isEqualTo(tipo);
            assertThat(envio.expiraEm()).isEqualTo(AGORA.plus(CapaCursoService.VALIDADE_UPLOAD));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"image/gif", "image/svg+xml", "text/html", "image/webp"})
        void recusaTipoNaoAceito(String tipo) {
            assertThatThrownBy(() -> service.iniciarEnvio(1L, tipo, 10))
                    .isInstanceOf(ImagemInvalidaException.class)
                    .hasMessage("Envie uma imagem JPEG ou PNG.");
            verify(storage, never()).urlUpload(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("recusa um byte acima de 5 MB")
        void recusaTamanhoAcimaDoLimite() {
            assertThatThrownBy(() -> service.iniciarEnvio(1L, "image/png", 5L * 1024 * 1024 + 1))
                    .isInstanceOf(ImagemInvalidaException.class)
                    .hasMessage("A imagem pode ter no máximo 5 MB.");
        }

        @Test
        @DisplayName("curso inexistente responde não encontrado")
        void cursoInexistente() {
            when(cursoAdmService.buscar(9L)).thenThrow(CursoAdmService.naoEncontrado());

            assertThatThrownBy(() -> service.iniciarEnvio(9L, "image/png", 10))
                    .isInstanceOf(NaoEncontradoException.class);
            verify(storage, never()).urlUpload(anyString(), anyString(), any());
        }
    }

    @Nested
    class Confirmar {

        @Test
        @DisplayName("grava a imagem processada em capas/, apaga o original e a capa anterior")
        void confirma() {
            when(storage.info(CHAVE)).thenReturn(Optional.of(new InfoArquivo(3, "image/png")));
            when(storage.baixar(CHAVE)).thenReturn(ORIGINAL);
            when(processador.normalizar(ORIGINAL, 1280, 720)).thenReturn(PROCESSADA);
            when(cursoAdmService.trocarCapa(eq(1L), startsWith("capas/1/")))
                    .thenReturn(Optional.of("capas/1/antiga.jpg"));

            service.confirmar(1L, CHAVE);

            ArgumentCaptor<String> capa = ArgumentCaptor.forClass(String.class);
            verify(storage).gravar(capa.capture(), eq(PROCESSADA), eq("image/jpeg"));
            assertThat(capa.getValue()).matches("capas/1/[0-9a-f-]{36}\\.jpg");
            verify(storage).excluir(CHAVE);
            verify(storage).excluir("capas/1/antiga.jpg");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "chave de outro curso,          uploads/capas/2/0b1e8f6a-4c2d-4e8b-9a51-7f3c2d1e0a9b",
            "fora da pasta de uploads,      capas/1/0b1e8f6a-4c2d-4e8b-9a51-7f3c2d1e0a9b",
            "subindo de pasta,              uploads/capas/1/../../videos/x",
            "nome que não é um uuid,        uploads/capas/1/imagem.png",
        })
        void recusaChaveQueNaoFoiGeradaParaOCurso(String caso, String chave) {
            assertThatThrownBy(() -> service.confirmar(1L, chave))
                    .isInstanceOf(ImagemInvalidaException.class)
                    .hasMessage("Envio de capa inválido. Comece um novo envio.");
            verify(storage, never()).info(anyString());
            verify(storage, never()).excluir(anyString());
        }

        @Test
        @DisplayName("arquivo não enviado pede um novo envio")
        void naoEnviado() {
            when(storage.info(CHAVE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.confirmar(1L, CHAVE))
                    .isInstanceOf(ImagemInvalidaException.class)
                    .hasMessage("A imagem não foi enviada. Comece um novo envio.");
            verify(cursoAdmService, never()).trocarCapa(any(), any());
        }

        @Test
        @DisplayName("arquivo acima de 5 MB é apagado sem ser baixado")
        void acimaDoLimite() {
            when(storage.info(CHAVE)).thenReturn(Optional.of(new InfoArquivo(5L * 1024 * 1024 + 1, "image/png")));

            assertThatThrownBy(() -> service.confirmar(1L, CHAVE))
                    .isInstanceOf(ImagemInvalidaException.class)
                    .hasMessage("A imagem pode ter no máximo 5 MB.");
            verify(storage, never()).baixar(anyString());
            verify(storage).excluir(CHAVE);
            verify(cursoAdmService, never()).trocarCapa(any(), any());
        }

        @Test
        @DisplayName("imagem inválida apaga o original e não troca a capa")
        void imagemInvalida() {
            when(storage.info(CHAVE)).thenReturn(Optional.of(new InfoArquivo(3, "image/png")));
            when(storage.baixar(CHAVE)).thenReturn(ORIGINAL);
            when(processador.normalizar(ORIGINAL, 1280, 720))
                    .thenThrow(new ImagemInvalidaException("Envie uma imagem JPEG ou PNG."));

            assertThatThrownBy(() -> service.confirmar(1L, CHAVE)).isInstanceOf(ImagemInvalidaException.class);
            verify(storage).excluir(CHAVE);
            verify(storage, never()).gravar(anyString(), any(), anyString());
            verify(cursoAdmService, never()).trocarCapa(any(), any());
        }

        @Test
        @DisplayName("falha ao gravar no banco apaga a imagem processada")
        void falhaNoBanco() {
            when(storage.info(CHAVE)).thenReturn(Optional.of(new InfoArquivo(3, "image/png")));
            when(storage.baixar(CHAVE)).thenReturn(ORIGINAL);
            when(processador.normalizar(ORIGINAL, 1280, 720)).thenReturn(PROCESSADA);
            when(cursoAdmService.trocarCapa(eq(1L), anyString())).thenThrow(new IllegalStateException("banco fora"));

            assertThatThrownBy(() -> service.confirmar(1L, CHAVE)).isInstanceOf(IllegalStateException.class);
            ArgumentCaptor<String> capa = ArgumentCaptor.forClass(String.class);
            verify(storage).gravar(capa.capture(), eq(PROCESSADA), eq("image/jpeg"));
            verify(storage).excluir(capa.getValue());
        }
    }

    @Test
    @DisplayName("remover limpa a capa e apaga o arquivo")
    void remover() {
        when(cursoAdmService.trocarCapa(1L, null)).thenReturn(Optional.of("capas/1/a.jpg"));

        service.remover(1L);

        verify(storage).excluir("capas/1/a.jpg");
    }

    @Test
    @DisplayName("excluir curso sem capa não mexe no bucket")
    void excluirCursoSemCapa() {
        when(cursoAdmService.excluir(1L)).thenReturn(Optional.empty());

        service.excluirCurso(1L);

        verify(storage, never()).excluir(anyString());
    }

    @Test
    @DisplayName("url só existe para curso com capa")
    void url() throws Exception {
        when(storage.urlDownload("capas/1/a.jpg", CapaCursoService.VALIDADE_DOWNLOAD))
                .thenReturn(url("http://s3/capa"));

        assertThat(service.url(curso(1, "java"))).isEmpty();
        assertThat(service.url(comCapa(curso(1, "java"), "capas/1/a.jpg"))).contains(url("http://s3/capa"));
    }
}
