package br.com.celke.cursos.matricula.application;

import static br.com.celke.cursos.support.fixtures.MatriculaFixture.cancelada;
import static br.com.celke.cursos.support.fixtures.MatriculaFixture.matricula;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.ModuloConteudo;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.TurmaResumo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.matricula.application.AlunoCursosService.MeuCurso;
import br.com.celke.cursos.matricula.domain.AcessoAulaPolicy;
import br.com.celke.cursos.matricula.domain.FiltroMeusCursos;
import br.com.celke.cursos.matricula.domain.Matricula;
import br.com.celke.cursos.matricula.domain.SituacaoMatricula;
import br.com.celke.cursos.matricula.domain.StatusMatricula;
import br.com.celke.cursos.matricula.infra.MatriculaRepository;
import br.com.celke.cursos.shared.erro.AcessoNegadoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlunoCursosServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final Instant ONTEM = AGORA.minusSeconds(86_400);
    private static final Instant AMANHA = AGORA.plusSeconds(86_400);

    @Mock
    private MatriculaRepository matriculas;

    @Mock
    private ConteudoTurmaService conteudoTurmaService;

    private AlunoCursosService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);
        service = new AlunoCursosService(matriculas, conteudoTurmaService, new AcessoAulaPolicy(clock), clock);
    }

    private static TurmaResumo turma(long id, String cursoTitulo) {
        return new TurmaResumo(
                id, "Turma " + id, StatusTurma.ATIVA, id, cursoTitulo, "c" + id, StatusCurso.PUBLICADO, null);
    }

    @Test
    @DisplayName("meus cursos filtra pela aba e ordena pelo título do curso")
    void meusCursos() {
        Matricula react = matricula(1, 7, 10, ONTEM, null);
        Matricula angular = matricula(2, 7, 20, ONTEM, AMANHA);
        Matricula vencida = matricula(3, 7, 30, ONTEM.minusSeconds(86_400), ONTEM);
        when(matriculas.findByUsuarioIdAndStatus(7L, StatusMatricula.ATIVA))
                .thenReturn(List.of(react, angular, vencida));
        when(conteudoTurmaService.turmas(List.of(10L, 20L)))
                .thenReturn(Map.of(10L, turma(10, "React"), 20L, turma(20, "Angular")));

        List<MeuCurso> ativos = service.meusCursos(7L, FiltroMeusCursos.ATIVOS);

        assertThat(ativos).extracting(c -> c.turma().cursoTitulo()).containsExactly("Angular", "React");
        assertThat(ativos).extracting(MeuCurso::situacao).containsOnly(SituacaoMatricula.VIGENTE);
    }

    @Test
    @DisplayName("turma com matrícula vigente traz o conteúdo ativo")
    void turmaVigente() {
        Matricula matricula = matricula(1, 7, 10, ONTEM, AMANHA);
        List<ModuloConteudo> conteudo = List.of(new ModuloConteudo(1L, "Módulo", List.of()));
        when(conteudoTurmaService.turma(10L)).thenReturn(Optional.of(turma(10, "React")));
        when(matriculas.findByUsuarioIdAndTurmaIdAndStatus(7L, 10L, StatusMatricula.ATIVA))
                .thenReturn(Optional.of(matricula));
        when(conteudoTurmaService.conteudoAtivo(10L)).thenReturn(conteudo);

        var turma = service.turma(7L, 10L);

        assertThat(turma.matricula()).isSameAs(matricula);
        assertThat(turma.modulos()).isEqualTo(conteudo);
    }

    @Test
    @DisplayName("turma sem matrícula, vencida ou ainda não iniciada responde 403 sem carregar o conteúdo")
    void turmaSemMatriculaVigente() {
        when(conteudoTurmaService.turma(10L)).thenReturn(Optional.of(turma(10, "React")));
        when(matriculas.findByUsuarioIdAndTurmaIdAndStatus(7L, 10L, StatusMatricula.ATIVA))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(matricula(1, 7, 10, ONTEM.minusSeconds(60), ONTEM)))
                .thenReturn(Optional.of(matricula(1, 7, 10, AMANHA, null)));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.turma(7L, 10L))
                    .isInstanceOf(AcessoNegadoException.class)
                    .hasMessage("Você não tem acesso a esta turma. Verifique o período da sua matrícula.");
        }
        verify(conteudoTurmaService, never()).conteudoAtivo(10L);
    }

    @Test
    @DisplayName("turma inexistente responde não encontrada")
    void turmaInexistente() {
        when(conteudoTurmaService.turma(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.turma(7L, 9L)).isInstanceOf(NaoEncontradoException.class);
    }

    @Test
    @DisplayName("arquiva e desarquiva a própria matrícula")
    void arquivar() {
        Matricula matricula = matricula(1, 7, 10, ONTEM, null);
        when(matriculas.findByIdAndUsuarioId(1L, 7L)).thenReturn(Optional.of(matricula));

        service.arquivar(7L, 1L);
        assertThat(matricula.getArquivadaEm()).isEqualTo(AGORA);
        service.desarquivar(7L, 1L);
        assertThat(matricula.isArquivada()).isFalse();
    }

    @Test
    @DisplayName("matrícula de outro aluno ou cancelada responde 404")
    void matriculaDeOutroAlunoOuCancelada() {
        when(matriculas.findByIdAndUsuarioId(1L, 8L)).thenReturn(Optional.empty());
        when(matriculas.findByIdAndUsuarioId(2L, 7L))
                .thenReturn(Optional.of(cancelada(matricula(2, 7, 10, ONTEM, null))));

        assertThatThrownBy(() -> service.arquivar(8L, 1L))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Matrícula não encontrada.");
        assertThatThrownBy(() -> service.desarquivar(7L, 2L)).isInstanceOf(NaoEncontradoException.class);
    }
}
