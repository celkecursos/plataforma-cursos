package br.com.celke.cursos.curso.application;

import static br.com.celke.cursos.support.fixtures.CursoFixture.comCapa;
import static br.com.celke.cursos.support.fixtures.CursoFixture.curso;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.domain.Curso;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.infra.CursoRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class CursoAdmServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private CursoRepository cursos;

    @Mock
    private TurmaRepository turmas;

    private CursoAdmService service;

    @BeforeEach
    void setUp() {
        service = new CursoAdmService(cursos, turmas, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Nested
    class Criar {

        @Test
        @DisplayName("sem slug, gera a partir do título; nasce em rascunho com os textos aparados")
        void geraSlug() {
            when(cursos.existsBySlug("programacao-java")).thenReturn(false);
            when(cursos.save(any(Curso.class))).thenAnswer(i -> i.getArgument(0));

            Curso curso = service.criar(new DadosCurso("  Programação Java ", null, " Resumo ", "  "));

            assertThat(curso.getSlug()).isEqualTo("programacao-java");
            assertThat(curso.getTitulo()).isEqualTo("Programação Java");
            assertThat(curso.getResumo()).isEqualTo("Resumo");
            assertThat(curso.getDescricao()).isNull();
            assertThat(curso.getStatus()).isEqualTo(StatusCurso.RASCUNHO);
            assertThat(curso.getCriadoEm()).isEqualTo(AGORA);
        }

        @Test
        @DisplayName("usa o slug informado")
        void usaSlugInformado() {
            when(cursos.existsBySlug("java")).thenReturn(false);
            when(cursos.save(any(Curso.class))).thenAnswer(i -> i.getArgument(0));

            assertThat(service.criar(new DadosCurso("Programação Java", "java", "Resumo", null))
                            .getSlug())
                    .isEqualTo("java");
        }

        @Test
        @DisplayName("rejeita slug já cadastrado")
        void rejeitaSlugJaCadastrado() {
            when(cursos.existsBySlug("java")).thenReturn(true);

            assertThatThrownBy(() -> service.criar(new DadosCurso("Java", null, "Resumo", null)))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("Já existe um curso com o slug \"java\".");
            verify(cursos, never()).save(any());
        }

        @Test
        @DisplayName("título só com símbolos e sem slug pede o slug")
        void tituloSemLetras() {
            assertThatThrownBy(() -> service.criar(new DadosCurso("!!!", " ", "Resumo", null)))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("Não foi possível gerar o slug a partir do título. Informe o slug.");
            verify(cursos, never()).save(any());
        }
    }

    @Nested
    class Atualizar {

        @Test
        @DisplayName("altera os dados e mantém o mesmo slug do próprio curso")
        void atualiza() {
            Curso curso = curso(1, "java");
            when(cursos.findById(1L)).thenReturn(Optional.of(curso));
            when(cursos.existsBySlugAndIdNot("java", 1L)).thenReturn(false);

            service.atualizar(1L, new DadosCurso("Java Moderno", "java", "Novo resumo", "Descrição"));

            assertThat(curso.getTitulo()).isEqualTo("Java Moderno");
            assertThat(curso.getDescricao()).isEqualTo("Descrição");
            assertThat(curso.getAtualizadoEm()).isEqualTo(AGORA);
        }

        @Test
        @DisplayName("rejeita slug de outro curso")
        void rejeitaSlugDeOutroCurso() {
            Curso curso = curso(1, "java");
            when(cursos.findById(1L)).thenReturn(Optional.of(curso));
            when(cursos.existsBySlugAndIdNot("react", 1L)).thenReturn(true);

            assertThatThrownBy(() -> service.atualizar(1L, new DadosCurso("Java", "react", "Resumo", null)))
                    .isInstanceOf(ConflitoException.class);
            assertThat(curso.getSlug()).isEqualTo("java");
        }

        @Test
        @DisplayName("curso inexistente responde não encontrado")
        void inexistente() {
            when(cursos.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.atualizar(9L, new DadosCurso("Java", null, "Resumo", null)))
                    .isInstanceOf(NaoEncontradoException.class)
                    .hasMessage("Curso não encontrado.");
        }
    }

    @Test
    @DisplayName("listar ignora a ordenação do cliente e filtra pelo status quando informado")
    void listar() {
        Pageable pedido = PageRequest.of(2, 10, Sort.by("senha"));
        Pageable esperado = PageRequest.of(2, 10, Sort.by("titulo", "id"));
        when(cursos.findAll(esperado)).thenReturn(Page.empty());
        when(cursos.findByStatus(StatusCurso.PUBLICADO, esperado)).thenReturn(Page.empty());

        service.listar(null, pedido);
        service.listar(StatusCurso.PUBLICADO, pedido);

        verify(cursos).findAll(esperado);
        verify(cursos).findByStatus(StatusCurso.PUBLICADO, esperado);
    }

    @Test
    @DisplayName("alterar status muda o status e a data de atualização")
    void alterarStatus() {
        Curso curso = curso(1, "java");
        when(cursos.findById(1L)).thenReturn(Optional.of(curso));

        service.alterarStatus(1L, StatusCurso.PUBLICADO);

        assertThat(curso.getStatus()).isEqualTo(StatusCurso.PUBLICADO);
        assertThat(curso.getAtualizadoEm()).isEqualTo(AGORA);
    }

    @Nested
    class Excluir {

        @Test
        @DisplayName("curso sem turmas é excluído e devolve a capa para apagar")
        void semTurmas() {
            Curso curso = comCapa(curso(1, "java"), "capas/1/a.jpg");
            when(cursos.findById(1L)).thenReturn(Optional.of(curso));
            when(turmas.existsByCursoId(1L)).thenReturn(false);

            assertThat(service.excluir(1L)).contains("capas/1/a.jpg");
            verify(cursos).delete(curso);
        }

        @Test
        @DisplayName("curso com turmas não é excluído")
        void comTurmas() {
            when(cursos.findById(1L)).thenReturn(Optional.of(curso(1, "java")));
            when(turmas.existsByCursoId(1L)).thenReturn(true);

            assertThatThrownBy(() -> service.excluir(1L))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("O curso tem turmas. Exclua as turmas vazias ou arquive o curso.");
            verify(cursos, never()).delete(any());
        }
    }

    @Test
    @DisplayName("trocar capa grava a nova chave e devolve a anterior")
    void trocarCapa() {
        Curso curso = comCapa(curso(1, "java"), "capas/1/antiga.jpg");
        when(cursos.findById(1L)).thenReturn(Optional.of(curso));

        assertThat(service.trocarCapa(1L, "capas/1/nova.jpg")).contains("capas/1/antiga.jpg");
        assertThat(curso.getCapaChave()).isEqualTo("capas/1/nova.jpg");
    }
}
