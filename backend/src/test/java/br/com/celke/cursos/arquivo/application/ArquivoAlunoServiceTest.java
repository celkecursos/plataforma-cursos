package br.com.celke.cursos.arquivo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.arquivo.domain.ArquivoApoio;
import br.com.celke.cursos.arquivo.domain.FormatoArquivo;
import br.com.celke.cursos.arquivo.infra.ArquivoApoioRepository;
import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.ModuloContexto;
import br.com.celke.cursos.matricula.application.AcessoAulaService;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.erro.AcessoNegadoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.net.URI;
import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArquivoAlunoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final Duration CINCO_MINUTOS = Duration.ofMinutes(5);

    @Mock
    private ArquivoApoioRepository arquivos;

    @Mock
    private AcessoAulaService acessoAulaService;

    @Mock
    private ConteudoTurmaService conteudoTurmaService;

    @Mock
    private StorageService storage;

    private ArquivoAlunoService service;

    @BeforeEach
    void criar() {
        service = new ArquivoAlunoService(
                arquivos, acessoAulaService, conteudoTurmaService, storage, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static ArquivoApoio daAula() {
        return ArquivoApoio.daAula(9L, "Código", "aula.zip", "arquivos/aulas/9/x", FormatoArquivo.ZIP, 6, AGORA);
    }

    private static ArquivoApoio doModulo() {
        return ArquivoApoio.doModulo(
                4L, "Apostila", "apostila.pdf", "arquivos/modulos/4/y", FormatoArquivo.PDF, 9, AGORA);
    }

    private static URL url() throws Exception {
        return URI.create("http://media.localhost/assinado").toURL();
    }

    @Test
    @DisplayName("arquivos da aula passam pela política da aula")
    void listaDaAula() {
        when(arquivos.findByAulaIdOrderById(9L)).thenReturn(List.of(daAula()));

        assertThat(service.daAula(7L, 9L)).hasSize(1);
        verify(acessoAulaService).exigirAcesso(7L, 9L);
    }

    @Test
    @DisplayName("arquivos do módulo exigem acesso à turma do módulo; módulo inativo ou inexistente responde 404")
    void listaDoModulo() {
        when(conteudoTurmaService.moduloAtivo(4L)).thenReturn(Optional.of(new ModuloContexto(4L, "M", 5L)));
        when(conteudoTurmaService.moduloAtivo(8L)).thenReturn(Optional.empty());
        when(arquivos.findByModuloIdOrderById(4L)).thenReturn(List.of(doModulo()));

        assertThat(service.doModulo(7L, 4L)).hasSize(1);
        verify(acessoAulaService).exigirAcessoTurma(7L, 5L);
        assertThatThrownBy(() -> service.doModulo(7L, 8L))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Módulo não encontrado.");
    }

    @Test
    @DisplayName("download do arquivo da aula: URL de 5 minutos, como anexo com o nome do arquivo")
    void downloadDaAula() throws Exception {
        when(arquivos.findById(1L)).thenReturn(Optional.of(daAula()));
        when(storage.urlDownload("arquivos/aulas/9/x", CINCO_MINUTOS, "aula.zip"))
                .thenReturn(url());

        var download = service.download(7L, 1L);

        verify(acessoAulaService).exigirAcesso(7L, 9L);
        assertThat(download.url()).isEqualTo(url());
        assertThat(download.expiraEm()).isEqualTo(AGORA.plus(CINCO_MINUTOS));
    }

    @Test
    @DisplayName("download do arquivo do módulo passa pela turma do módulo")
    void downloadDoModulo() throws Exception {
        when(arquivos.findById(2L)).thenReturn(Optional.of(doModulo()));
        when(conteudoTurmaService.moduloAtivo(4L)).thenReturn(Optional.of(new ModuloContexto(4L, "M", 5L)));
        when(storage.urlDownload("arquivos/modulos/4/y", CINCO_MINUTOS, "apostila.pdf"))
                .thenReturn(url());

        assertThat(service.download(7L, 2L).url()).isEqualTo(url());
        verify(acessoAulaService).exigirAcessoTurma(7L, 5L);
    }

    @Test
    @DisplayName("sem acesso, nenhuma URL é assinada; arquivo inexistente responde 404")
    void semAcesso() {
        when(arquivos.findById(1L)).thenReturn(Optional.of(daAula()));
        when(arquivos.findById(3L)).thenReturn(Optional.empty());
        doThrow(new AcessoNegadoException("sem acesso")).when(acessoAulaService).exigirAcesso(7L, 9L);

        assertThatThrownBy(() -> service.download(7L, 1L)).isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.download(7L, 3L))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Arquivo não encontrado.");
        verify(storage, never()).urlDownload(anyString(), any(), anyString());
    }
}
