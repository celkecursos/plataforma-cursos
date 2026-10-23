package br.com.celke.cursos.arquivo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.arquivo.domain.ArquivoApoio;
import br.com.celke.cursos.arquivo.domain.FormatoArquivo;
import br.com.celke.cursos.arquivo.infra.ArquivoApoioRepository;
import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.armazenamento.StorageService.InfoArquivo;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArquivoAdmServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final String UUID = "0b6b6b6e-1c1d-4e4f-8a8b-9c9d0e0f1a1b";
    private static final String UPLOAD = "uploads/arquivos/aulas/9/" + UUID;
    private static final String DESTINO = "arquivos/aulas/9/" + UUID;
    private static final byte[] ZIP = {'P', 'K', 3, 4, 20, 0};

    @Mock
    private ArquivoApoioRepository arquivos;

    @Mock
    private ConteudoTurmaService conteudoTurmaService;

    @Mock
    private StorageService storage;

    private ArquivoAdmService service;

    @BeforeEach
    void criar() {
        service = new ArquivoAdmService(arquivos, conteudoTurmaService, storage, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private void aulaExiste() {
        when(conteudoTurmaService.aulaExiste(9L)).thenReturn(true);
    }

    @Nested
    class Iniciar {

        @Test
        @DisplayName("aula: URL de PUT em uploads/arquivos/aulas/<id>/<uuid> com o tipo informado")
        void aula() throws Exception {
            aulaExiste();
            when(storage.urlUpload(
                            startsWith("uploads/arquivos/aulas/9/"), eq("application/zip"), eq(Duration.ofMinutes(15))))
                    .thenReturn(URI.create("http://media.localhost/put").toURL());

            var envio = service.iniciarEnvio(DonoArquivo.aula(9L), "application/zip", 100);

            assertThat(envio.chave()).matches("uploads/arquivos/aulas/9/[0-9a-f-]{36}");
            assertThat(envio.contentType()).isEqualTo("application/zip");
            assertThat(envio.expiraEm()).isEqualTo(AGORA.plus(Duration.ofMinutes(15)));
        }

        @Test
        @DisplayName("módulo usa a pasta do módulo")
        void modulo() throws Exception {
            when(conteudoTurmaService.moduloExiste(4L)).thenReturn(true);
            when(storage.urlUpload(startsWith("uploads/arquivos/modulos/4/"), eq("application/pdf"), any()))
                    .thenReturn(URI.create("http://media.localhost/put").toURL());

            assertThat(service.iniciarEnvio(DonoArquivo.modulo(4L), "application/pdf", 1)
                            .chave())
                    .startsWith("uploads/arquivos/modulos/4/");
        }

        @Test
        @DisplayName("tipo fora da lista e tamanho acima de 200 MB são recusados sem assinar")
        void recusas() {
            aulaExiste();

            assertThatThrownBy(() -> service.iniciarEnvio(DonoArquivo.aula(9L), "text/html", 10))
                    .isInstanceOf(ArquivoInvalidoException.class)
                    .hasMessage("Envie um arquivo ZIP, PDF ou TXT.");
            assertThatThrownBy(() -> service.iniciarEnvio(
                            DonoArquivo.aula(9L), "application/zip", ArquivoAdmService.TAMANHO_MAXIMO + 1))
                    .isInstanceOf(ArquivoInvalidoException.class)
                    .hasMessage("O arquivo pode ter no máximo 200 MB.");
            verify(storage, never()).urlUpload(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("aula ou módulo inexistente responde 404")
        void donoInexistente() {
            when(conteudoTurmaService.aulaExiste(9L)).thenReturn(false);
            when(conteudoTurmaService.moduloExiste(4L)).thenReturn(false);

            assertThatThrownBy(() -> service.iniciarEnvio(DonoArquivo.aula(9L), "application/zip", 1))
                    .isInstanceOf(NaoEncontradoException.class)
                    .hasMessage("Aula não encontrada.");
            assertThatThrownBy(() -> service.listar(DonoArquivo.modulo(4L)))
                    .isInstanceOf(NaoEncontradoException.class)
                    .hasMessage("Módulo não encontrado.");
        }
    }

    @Nested
    class Confirmar {

        @Test
        @DisplayName("confere, move para arquivos/, apaga o envio e registra com o nome limpo")
        void confirma() {
            aulaExiste();
            when(storage.info(UPLOAD)).thenReturn(Optional.of(new InfoArquivo(6, "application/zip")));
            when(storage.baixarInicio(UPLOAD, FormatoArquivo.BYTES_CONFERENCIA)).thenReturn(ZIP);
            when(arquivos.save(any(ArquivoApoio.class))).thenAnswer(i -> i.getArgument(0));

            ArquivoApoio arquivo = service.confirmar(DonoArquivo.aula(9L), UPLOAD, " Código-fonte ", "Aula 1.zip");

            verify(storage).copiar(UPLOAD, DESTINO);
            verify(storage).excluir(UPLOAD);
            assertThat(arquivo.getChave()).isEqualTo(DESTINO);
            assertThat(arquivo.getAulaId()).isEqualTo(9L);
            assertThat(arquivo.getModuloId()).isNull();
            assertThat(arquivo.getTitulo()).isEqualTo("Código-fonte");
            assertThat(arquivo.getNomeArquivo()).isEqualTo("Aula-1.zip");
            assertThat(arquivo.getContentType()).isEqualTo("application/zip");
            assertThat(arquivo.getTamanho()).isEqualTo(6);
            assertThat(arquivo.getCriadoEm()).isEqualTo(AGORA);
        }

        @Test
        @DisplayName("chave de outro dono ou fora do formato é recusada sem tocar no bucket")
        void chaveInvalida() {
            aulaExiste();
            for (String chave : List.of(
                    "uploads/arquivos/aulas/10/" + UUID,
                    "uploads/arquivos/aulas/9/../../capas/x",
                    "arquivos/aulas/9/" + UUID)) {
                assertThatThrownBy(() -> service.confirmar(DonoArquivo.aula(9L), chave, "t", "a.zip"))
                        .isInstanceOf(ArquivoInvalidoException.class)
                        .hasMessage("Envio de arquivo inválido. Comece um novo envio.");
            }
            verify(storage, never()).info(anyString());
            verify(storage, never()).excluir(anyString());
        }

        @Test
        @DisplayName("arquivo não enviado")
        void naoEnviado() {
            aulaExiste();
            when(storage.info(UPLOAD)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.confirmar(DonoArquivo.aula(9L), UPLOAD, "t", "a.zip"))
                    .isInstanceOf(ArquivoInvalidoException.class)
                    .hasMessage("O arquivo não foi enviado. Comece um novo envio.");
            verify(arquivos, never()).save(any());
        }

        @Test
        @DisplayName("conteúdo que não confere com o tipo (extensão falsa) é recusado e o envio apagado")
        void conteudoFalso() {
            aulaExiste();
            when(storage.info(UPLOAD)).thenReturn(Optional.of(new InfoArquivo(20, "application/zip")));
            when(storage.baixarInicio(UPLOAD, FormatoArquivo.BYTES_CONFERENCIA))
                    .thenReturn("<html><script>".getBytes());

            assertThatThrownBy(() -> service.confirmar(DonoArquivo.aula(9L), UPLOAD, "t", "a.zip"))
                    .isInstanceOf(ArquivoInvalidoException.class)
                    .hasMessage("O conteúdo do arquivo não confere com o tipo informado.");
            verify(storage).excluir(UPLOAD);
            verify(storage, never()).copiar(anyString(), anyString());
            verify(arquivos, never()).save(any());
        }

        @Test
        @DisplayName("tamanho real acima do limite, vazio ou tipo gravado fora da lista: recusado e apagado")
        void recusasDoArquivoReal() {
            aulaExiste();
            when(storage.info(UPLOAD))
                    .thenReturn(Optional.of(new InfoArquivo(ArquivoAdmService.TAMANHO_MAXIMO + 1, "application/zip")))
                    .thenReturn(Optional.of(new InfoArquivo(0, "application/zip")))
                    .thenReturn(Optional.of(new InfoArquivo(10, "text/html")));

            assertThatThrownBy(() -> service.confirmar(DonoArquivo.aula(9L), UPLOAD, "t", "a.zip"))
                    .hasMessage("O arquivo pode ter no máximo 200 MB.");
            assertThatThrownBy(() -> service.confirmar(DonoArquivo.aula(9L), UPLOAD, "t", "a.zip"))
                    .hasMessage("O arquivo está vazio.");
            assertThatThrownBy(() -> service.confirmar(DonoArquivo.aula(9L), UPLOAD, "t", "a.zip"))
                    .hasMessage("Envie um arquivo ZIP, PDF ou TXT.");
            verify(storage, times(3)).excluir(UPLOAD);
            verify(arquivos, never()).save(any());
        }

        @Test
        @DisplayName("falha ao registrar apaga o arquivo já movido")
        void falhaAoRegistrar() {
            aulaExiste();
            when(storage.info(UPLOAD)).thenReturn(Optional.of(new InfoArquivo(6, "application/zip")));
            when(storage.baixarInicio(UPLOAD, FormatoArquivo.BYTES_CONFERENCIA)).thenReturn(ZIP);
            when(arquivos.save(any(ArquivoApoio.class))).thenThrow(new IllegalStateException("banco fora"));

            assertThatThrownBy(() -> service.confirmar(DonoArquivo.aula(9L), UPLOAD, "t", "a.zip"))
                    .isInstanceOf(IllegalStateException.class);
            verify(storage).excluir(DESTINO);
        }

        @Test
        @DisplayName("arquivo do módulo fica com o id do módulo")
        void modulo() {
            String upload = "uploads/arquivos/modulos/4/" + UUID;
            when(conteudoTurmaService.moduloExiste(4L)).thenReturn(true);
            when(storage.info(upload)).thenReturn(Optional.of(new InfoArquivo(9, "application/pdf")));
            when(storage.baixarInicio(upload, FormatoArquivo.BYTES_CONFERENCIA)).thenReturn("%PDF-1.7".getBytes());
            ArgumentCaptor<ArquivoApoio> captor = ArgumentCaptor.forClass(ArquivoApoio.class);
            when(arquivos.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

            service.confirmar(DonoArquivo.modulo(4L), upload, "Apostila", "apostila");

            assertThat(captor.getValue().getModuloId()).isEqualTo(4L);
            assertThat(captor.getValue().getAulaId()).isNull();
            assertThat(captor.getValue().getNomeArquivo()).isEqualTo("apostila.pdf");
            verify(storage).copiar(upload, "arquivos/modulos/4/" + UUID);
        }
    }

    @Test
    @DisplayName("listar devolve os arquivos do dono")
    void listar() {
        aulaExiste();
        List<ArquivoApoio> lista = List.of();
        when(arquivos.findByAulaIdOrderById(9L)).thenReturn(lista);

        assertThat(service.listar(DonoArquivo.aula(9L))).isSameAs(lista);
    }

    @Test
    @DisplayName("excluir apaga o registro e o arquivo; inexistente responde 404")
    void excluir() {
        ArquivoApoio arquivo = ArquivoApoio.daAula(9L, "t", "a.zip", DESTINO, FormatoArquivo.ZIP, 6, AGORA);
        when(arquivos.findById(1L)).thenReturn(Optional.of(arquivo));
        when(arquivos.findById(2L)).thenReturn(Optional.empty());

        service.excluir(1L);

        verify(arquivos).delete(arquivo);
        verify(storage).excluir(DESTINO);
        assertThatThrownBy(() -> service.excluir(2L))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Arquivo não encontrado.");
    }

    @Test
    @DisplayName("falha do bucket ao copiar não registra nada e ainda apaga o envio")
    void falhaAoCopiar() {
        aulaExiste();
        when(storage.info(UPLOAD)).thenReturn(Optional.of(new InfoArquivo(6, "application/zip")));
        when(storage.baixarInicio(UPLOAD, FormatoArquivo.BYTES_CONFERENCIA)).thenReturn(ZIP);
        doThrow(new IllegalStateException("S3")).when(storage).copiar(UPLOAD, DESTINO);

        assertThatThrownBy(() -> service.confirmar(DonoArquivo.aula(9L), UPLOAD, "t", "a.zip"))
                .isInstanceOf(IllegalStateException.class);
        verify(storage).excluir(UPLOAD);
        verify(arquivos, never()).save(any());
    }
}
