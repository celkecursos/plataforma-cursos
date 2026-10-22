package br.com.celke.cursos.curso.application;

import static br.com.celke.cursos.support.fixtures.CursoFixture.aula;
import static br.com.celke.cursos.support.fixtures.CursoFixture.curso;
import static br.com.celke.cursos.support.fixtures.CursoFixture.modulo;
import static br.com.celke.cursos.support.fixtures.CursoFixture.publicado;
import static br.com.celke.cursos.support.fixtures.CursoFixture.turma;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.domain.Curso;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.curso.domain.Turma;
import br.com.celke.cursos.curso.infra.AulaRepository;
import br.com.celke.cursos.curso.infra.CursoRepository;
import br.com.celke.cursos.curso.infra.ModuloRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class CatalogoServiceTest {

    @Mock
    private CursoRepository cursos;

    @Mock
    private TurmaRepository turmas;

    @Mock
    private ModuloRepository modulos;

    @Mock
    private AulaRepository aulas;

    private CatalogoService service;
    private final Curso curso = publicado(curso(1, "java"));

    @BeforeEach
    void setUp() {
        service = new CatalogoService(cursos, turmas, modulos, aulas);
    }

    @Test
    @DisplayName("lista só publicados, em ordem alfabética, ignorando a ordenação do cliente")
    void listar() {
        when(cursos.findByStatus(StatusCurso.PUBLICADO, PageRequest.of(1, 5, Sort.by("titulo", "id"))))
                .thenReturn(Page.empty());

        service.listar(PageRequest.of(1, 5, Sort.by("capaChave")));

        verify(cursos).findByStatus(StatusCurso.PUBLICADO, PageRequest.of(1, 5, Sort.by("titulo", "id")));
    }

    @Test
    @DisplayName("curso não publicado responde não encontrado")
    void naoPublicado() {
        when(cursos.findBySlugAndStatus("rascunho", StatusCurso.PUBLICADO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detalhar("rascunho"))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Curso não encontrado.");
    }

    @Test
    @DisplayName("curso sem turma ativa aparece sem conteúdo")
    void semTurmaAtiva() {
        when(cursos.findBySlugAndStatus("java", StatusCurso.PUBLICADO)).thenReturn(Optional.of(curso));
        when(turmas.findFirstByCursoIdAndStatusOrderByIdDesc(1L, StatusTurma.ATIVA))
                .thenReturn(Optional.empty());

        assertThat(service.detalhar("java").modulos()).isEmpty();
    }

    @Test
    @DisplayName("agrupa as aulas ativas por módulo, na ordem; módulo sem aulas vem com lista vazia")
    void agrupaAulas() {
        Turma turma = turma(5, curso);
        var primeiro = modulo(10, turma, 1);
        var segundo = modulo(20, turma, 2);
        when(cursos.findBySlugAndStatus("java", StatusCurso.PUBLICADO)).thenReturn(Optional.of(curso));
        when(turmas.findFirstByCursoIdAndStatusOrderByIdDesc(1L, StatusTurma.ATIVA))
                .thenReturn(Optional.of(turma));
        when(modulos.findByTurmaIdAndStatusOrderByOrdem(5L, StatusConteudo.ATIVO))
                .thenReturn(List.of(primeiro, segundo));
        when(aulas.findByModuloIdInAndStatusOrderByOrdem(List.of(10L, 20L), StatusConteudo.ATIVO))
                .thenReturn(List.of(aula(100, primeiro, 1), aula(101, primeiro, 2)));

        var detalhe = service.detalhar("java");

        assertThat(detalhe.curso()).isSameAs(curso);
        assertThat(detalhe.modulos()).hasSize(2);
        assertThat(detalhe.modulos().get(0).aulas()).extracting(a -> a.getId()).containsExactly(100L, 101L);
        assertThat(detalhe.modulos().get(1).aulas()).isEmpty();
    }
}
