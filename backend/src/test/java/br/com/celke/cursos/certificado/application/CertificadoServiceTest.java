package br.com.celke.cursos.certificado.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.certificado.domain.Certificado;
import br.com.celke.cursos.certificado.domain.Certificado.DadosImpressos;
import br.com.celke.cursos.certificado.infra.CertificadoRepository;
import br.com.celke.cursos.certificado.infra.GeradorPdf;
import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.RegraCertificado;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.TurmaResumo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.progresso.application.ProgressoService;
import br.com.celke.cursos.progresso.application.ProgressoService.ProgressoTurma;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import java.net.URI;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CertificadoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private CertificadoRepository certificados;

    @Mock
    private ProgressoService progressoService;

    @Mock
    private ConteudoTurmaService conteudoTurmaService;

    @Mock
    private GeradorPdf geradorPdf;

    @Mock
    private StorageService storage;

    private CertificadoService service;

    @BeforeEach
    void criar() {
        service = new CertificadoService(
                certificados,
                progressoService,
                conteudoTurmaService,
                geradorPdf,
                storage,
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private void progresso(int percentual, int totalAulas) {
        when(progressoService.daTurma(7L, 5L)).thenReturn(new ProgressoTurma(5L, totalAulas, 0, percentual, List.of()));
    }

    private void regra(int minimo, Integer carga) {
        when(conteudoTurmaService.regraCertificado(5L)).thenReturn(Optional.of(new RegraCertificado(minimo, carga)));
    }

    private static Certificado existente() {
        return new Certificado(
                7L,
                5L,
                "K7QM-2XRP-H9TD",
                new DadosImpressos("Ana Souza", "Java", "T1", 40),
                100,
                "certificados/5/x.pdf",
                AGORA);
    }

    @Test
    @DisplayName("situação: percentual visto e exigido, concluído ou não")
    void situacao() {
        progresso(80, 10);
        regra(80, null);
        when(certificados.findByUsuarioIdAndTurmaId(7L, 5L)).thenReturn(Optional.empty());

        var situacao = service.situacao(7L, 5L);

        assertThat(situacao.percentualVisto()).isEqualTo(80);
        assertThat(situacao.percentualExigido()).isEqualTo(80);
        assertThat(situacao.concluiu()).isTrue();
        assertThat(situacao.certificado()).isEmpty();
    }

    @Test
    @DisplayName("emite: gera o PDF com os dados congelados, guarda no bucket e registra")
    void emite() {
        progresso(100, 3);
        regra(100, 40);
        when(certificados.findByUsuarioIdAndTurmaId(7L, 5L)).thenReturn(Optional.empty());
        when(conteudoTurmaService.turma(5L))
                .thenReturn(Optional.of(new TurmaResumo(
                        5L, "Turma 1", StatusTurma.ATIVA, 1L, "Java Moderno", "java", StatusCurso.PUBLICADO, null)));
        when(geradorPdf.gerar(any(), anyString(), eq(AGORA))).thenReturn(new byte[] {'%', 'P', 'D', 'F'});
        when(certificados.saveAndFlush(any(Certificado.class))).thenAnswer(i -> i.getArgument(0));

        var emissao = service.emitir(7L, 5L, "  Ana   Souza ");

        assertThat(emissao.novo()).isTrue();
        Certificado c = emissao.certificado();
        assertThat(c.getNomeCompleto()).isEqualTo("Ana Souza");
        assertThat(c.getCursoTitulo()).isEqualTo("Java Moderno");
        assertThat(c.getTurmaNome()).isEqualTo("Turma 1");
        assertThat(c.getCargaHorariaHoras()).isEqualTo(40);
        assertThat(c.getPercentualConcluido()).isEqualTo(100);
        assertThat(c.getEmitidoEm()).isEqualTo(AGORA);
        assertThat(c.getPdfChave()).isEqualTo("certificados/5/" + c.getCodigo() + ".pdf");
        verify(storage).gravar(c.getPdfChave(), new byte[] {'%', 'P', 'D', 'F'}, "application/pdf");
        ArgumentCaptor<DadosImpressos> dados = ArgumentCaptor.forClass(DadosImpressos.class);
        verify(geradorPdf).gerar(dados.capture(), eq(c.getCodigo()), eq(AGORA));
        assertThat(dados.getValue().nomeCompleto()).isEqualTo("Ana Souza");
    }

    @Test
    @DisplayName("já emitido: devolve o mesmo certificado, sem gerar outro PDF nem mudar o nome")
    void jaEmitido() {
        progresso(100, 3);
        regra(100, null);
        when(certificados.findByUsuarioIdAndTurmaId(7L, 5L)).thenReturn(Optional.of(existente()));

        var emissao = service.emitir(7L, 5L, "Outro Nome");

        assertThat(emissao.novo()).isFalse();
        assertThat(emissao.certificado().getNomeCompleto()).isEqualTo("Ana Souza");
        verify(geradorPdf, never()).gerar(any(), anyString(), any());
    }

    @Test
    @DisplayName("sem concluir: 409 com o percentual visto e o exigido")
    void naoConcluiu() {
        progresso(79, 10);
        regra(80, null);
        when(certificados.findByUsuarioIdAndTurmaId(7L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.emitir(7L, 5L, "Ana Souza"))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("Você ainda não concluiu a turma: 79% das aulas vistas, de 80% exigidos.");
        verify(storage, never()).gravar(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("nome sem sobrenome: 400 antes de qualquer geração")
    void nomeInvalido() {
        progresso(100, 3);
        regra(100, null);
        when(certificados.findByUsuarioIdAndTurmaId(7L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.emitir(7L, 5L, "Ana"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Informe o nome completo como deve sair no certificado.");
        verify(geradorPdf, never()).gerar(any(), anyString(), any());
    }

    @Test
    @DisplayName("duas emissões ao mesmo tempo: fica a primeira e o PDF da segunda é apagado")
    void corrida() {
        progresso(100, 3);
        regra(100, null);
        when(certificados.findByUsuarioIdAndTurmaId(7L, 5L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existente()));
        when(conteudoTurmaService.turma(5L))
                .thenReturn(Optional.of(
                        new TurmaResumo(5L, "T", StatusTurma.ATIVA, 1L, "C", "c", StatusCurso.PUBLICADO, null)));
        when(geradorPdf.gerar(any(), anyString(), any())).thenReturn(new byte[] {1});
        when(certificados.saveAndFlush(any(Certificado.class))).thenThrow(new DataIntegrityViolationException("uq"));

        var emissao = service.emitir(7L, 5L, "Ana Souza");

        assertThat(emissao.novo()).isFalse();
        assertThat(emissao.certificado().getCodigo()).isEqualTo("K7QM-2XRP-H9TD");
        verify(storage).excluir(startsWith("certificados/5/"));
    }

    @Test
    @DisplayName("download só do próprio certificado, por 5 minutos e com nome de arquivo")
    void download() throws Exception {
        when(certificados.findByIdAndUsuarioId(1L, 7L)).thenReturn(Optional.of(existente()));
        when(certificados.findByIdAndUsuarioId(1L, 8L)).thenReturn(Optional.empty());
        when(storage.urlDownload("certificados/5/x.pdf", Duration.ofMinutes(5), "certificado-K7QM-2XRP-H9TD.pdf"))
                .thenReturn(URI.create("http://media.localhost/pdf").toURL());

        assertThat(service.download(7L, 1L).expiraEm()).isEqualTo(AGORA.plus(Duration.ofMinutes(5)));
        assertThatThrownBy(() -> service.download(8L, 1L))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Certificado não encontrado.");
    }

    @Test
    @DisplayName("validação pública: código normalizado; inválido ou inexistente responde 404 sem consultar à toa")
    void validar() {
        when(certificados.findByCodigo("K7QM-2XRP-H9TD")).thenReturn(Optional.of(existente()));

        assertThat(service.validar(" k7qm-2xrp-h9td ").getNomeCompleto()).isEqualTo("Ana Souza");
        assertThatThrownBy(() -> service.validar("nao-e-codigo"))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Certificado não encontrado.");
        verify(certificados, never()).findByCodigo("nao-e-codigo");
    }
}
