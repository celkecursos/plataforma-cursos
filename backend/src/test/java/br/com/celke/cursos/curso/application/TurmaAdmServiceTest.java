package br.com.celke.cursos.curso.application;

import static br.com.celke.cursos.support.fixtures.CursoFixture.curso;
import static br.com.celke.cursos.support.fixtures.CursoFixture.turma;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.curso.domain.Turma;
import br.com.celke.cursos.curso.infra.CursoRepository;
import br.com.celke.cursos.curso.infra.ModuloRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class TurmaAdmServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private CursoRepository cursos;

    @Mock
    private TurmaRepository turmas;

    @Mock
    private ModuloRepository modulos;

    private TurmaAdmService service;

    @BeforeEach
    void setUp() {
        service = new TurmaAdmService(cursos, turmas, modulos, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("turma nova nasce em preparação, no curso informado")
    void criar() {
        var curso = curso(1, "java");
        when(cursos.findById(1L)).thenReturn(Optional.of(curso));
        when(turmas.save(any(Turma.class))).thenAnswer(i -> i.getArgument(0));

        Turma turma = service.criar(1L, " Turma 2026 ");

        assertThat(turma.getNome()).isEqualTo("Turma 2026");
        assertThat(turma.getStatus()).isEqualTo(StatusTurma.PREPARACAO);
        assertThat(turma.getCurso()).isSameAs(curso);
    }

    @Test
    @DisplayName("listar e criar em curso inexistente respondem não encontrado")
    void cursoInexistente() {
        when(cursos.existsById(9L)).thenReturn(false);
        when(cursos.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listar(9L)).isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> service.criar(9L, "Turma")).isInstanceOf(NaoEncontradoException.class);
        verify(turmas, never()).save(any());
    }

    @Test
    @DisplayName("renomeia e altera o status")
    void renomearEAlterarStatus() {
        Turma turma = turma(5, curso(1, "java"));
        when(turmas.findById(5L)).thenReturn(Optional.of(turma));

        service.renomear(5L, "Nova");
        service.alterarStatus(5L, StatusTurma.ATIVA);

        assertThat(turma.getNome()).isEqualTo("Nova");
        assertThat(turma.getStatus()).isEqualTo(StatusTurma.ATIVA);
    }

    @Test
    @DisplayName("turma sem módulos é excluída")
    void excluiVazia() {
        Turma turma = turma(5, curso(1, "java"));
        when(turmas.findById(5L)).thenReturn(Optional.of(turma));
        when(modulos.existsByTurmaId(5L)).thenReturn(false);

        service.excluir(5L);

        verify(turmas).delete(turma);
    }

    @Test
    @DisplayName("turma com módulos não é excluída")
    void naoExcluiComModulos() {
        when(turmas.findById(5L)).thenReturn(Optional.of(turma(5, curso(1, "java"))));
        when(modulos.existsByTurmaId(5L)).thenReturn(true);

        assertThatThrownBy(() -> service.excluir(5L))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("A turma tem módulos. Exclua os módulos ou encerre a turma.");
        verify(turmas, never()).delete(any());
    }

    @Test
    @DisplayName("turma com matrículas não é excluída (chave estrangeira no banco)")
    void naoExcluiComMatriculas() {
        when(turmas.findById(5L)).thenReturn(Optional.of(turma(5, curso(1, "java"))));
        when(modulos.existsByTurmaId(5L)).thenReturn(false);
        doThrow(new DataIntegrityViolationException("fk_matriculas"))
                .when(turmas)
                .flush();

        assertThatThrownBy(() -> service.excluir(5L))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("A turma tem matrículas. Encerre a turma.");
    }

    @Test
    @DisplayName("turma inexistente responde não encontrada")
    void inexistente() {
        when(turmas.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renomear(9L, "Nova"))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Turma não encontrada.");
    }
}
