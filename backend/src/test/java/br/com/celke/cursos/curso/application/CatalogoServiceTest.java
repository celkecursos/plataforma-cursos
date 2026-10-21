package br.com.celke.cursos.curso.application;

import static br.com.celke.cursos.support.fixtures.CursoFixture.curso;
import static br.com.celke.cursos.support.fixtures.CursoFixture.publicado;
import static br.com.celke.cursos.support.fixtures.CursoFixture.turma;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.application.ConteudoTurmaService.ModuloConteudo;
import br.com.celke.cursos.curso.domain.Curso;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.curso.infra.CursoRepository;
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
    private ConteudoTurmaService conteudoTurmaService;

    private CatalogoService service;
    private final Curso curso = publicado(curso(1, "java"));

    @BeforeEach
    void setUp() {
        service = new CatalogoService(cursos, turmas, conteudoTurmaService);
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
    @DisplayName("mostra o conteúdo ativo da turma ativa mais recente")
    void conteudoDaTurmaAtiva() {
        List<ModuloConteudo> conteudo = List.of(new ModuloConteudo(10L, "Introdução", List.of()));
        when(cursos.findBySlugAndStatus("java", StatusCurso.PUBLICADO)).thenReturn(Optional.of(curso));
        when(turmas.findFirstByCursoIdAndStatusOrderByIdDesc(1L, StatusTurma.ATIVA))
                .thenReturn(Optional.of(turma(5, curso)));
        when(conteudoTurmaService.conteudoAtivo(5L)).thenReturn(conteudo);

        var detalhe = service.detalhar("java");

        assertThat(detalhe.curso()).isSameAs(curso);
        assertThat(detalhe.modulos()).isEqualTo(conteudo);
    }
}
