package br.com.celke.cursos.curso.application;

import static br.com.celke.cursos.support.fixtures.CursoFixture.curso;
import static br.com.celke.cursos.support.fixtures.CursoFixture.modulo;
import static br.com.celke.cursos.support.fixtures.CursoFixture.turma;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.domain.Modulo;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import br.com.celke.cursos.curso.domain.Turma;
import br.com.celke.cursos.curso.infra.AulaRepository;
import br.com.celke.cursos.curso.infra.ModuloRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import java.time.Clock;
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
class ModuloAdmServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private TurmaRepository turmas;

    @Mock
    private ModuloRepository modulos;

    @Mock
    private AulaRepository aulas;

    private ModuloAdmService service;
    private final Turma turma = turma(5, curso(1, "java"));

    @BeforeEach
    void setUp() {
        service = new ModuloAdmService(turmas, modulos, aulas, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("módulo novo entra depois do último, com a turma travada")
    void criarNoFim() {
        when(turmas.travar(5L)).thenReturn(Optional.of(turma));
        when(modulos.findByTurmaIdOrderByOrdem(5L)).thenReturn(List.of(modulo(10, turma, 1), modulo(20, turma, 2)));
        when(modulos.save(any(Modulo.class))).thenAnswer(i -> i.getArgument(0));

        Modulo modulo = service.criar(5L, " Introdução ");

        assertThat(modulo.getOrdem()).isEqualTo(3);
        assertThat(modulo.getTitulo()).isEqualTo("Introdução");
        assertThat(modulo.getStatus()).isEqualTo(StatusConteudo.ATIVO);
    }

    @Test
    @DisplayName("turma inexistente: listar e criar respondem não encontrada")
    void turmaInexistente() {
        when(turmas.existsById(9L)).thenReturn(false);
        when(turmas.travar(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listar(9L)).isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> service.criar(9L, "Módulo")).isInstanceOf(NaoEncontradoException.class);
        verify(modulos, never()).save(any());
    }

    @Test
    @DisplayName("reordenar devolve os módulos na nova ordem")
    void reordenar() {
        when(turmas.travar(5L)).thenReturn(Optional.of(turma));
        when(modulos.findByTurmaIdOrderByOrdem(5L)).thenReturn(List.of(modulo(10, turma, 1), modulo(20, turma, 2)));

        assertThat(service.reordenar(5L, List.of(20L, 10L)))
                .extracting(Modulo::getId, Modulo::getOrdem)
                .containsExactly(tuple(20L, 1), tuple(10L, 2));
    }

    @Test
    @DisplayName("reordenar com lista incompleta não muda nada")
    void reordenarIncompleto() {
        List<Modulo> atuais = List.of(modulo(10, turma, 1), modulo(20, turma, 2));
        when(turmas.travar(5L)).thenReturn(Optional.of(turma));
        when(modulos.findByTurmaIdOrderByOrdem(5L)).thenReturn(atuais);

        assertThatThrownBy(() -> service.reordenar(5L, List.of(20L))).isInstanceOf(RegraNegocioException.class);
        assertThat(atuais).extracting(Modulo::getOrdem).containsExactly(1, 2);
    }

    @Test
    @DisplayName("excluir módulo sem aulas refaz a sequência dos que ficam")
    void excluirCompacta() {
        Modulo excluido = modulo(10, turma, 1);
        Modulo restante = modulo(20, turma, 2);
        when(modulos.findById(10L)).thenReturn(Optional.of(excluido));
        when(aulas.existsByModuloId(10L)).thenReturn(false);
        when(modulos.findByTurmaIdOrderByOrdem(5L)).thenReturn(List.of(restante));

        service.excluir(10L);

        verify(turmas).travar(5L);
        verify(modulos).delete(excluido);
        assertThat(restante.getOrdem()).isEqualTo(1);
    }

    @Test
    @DisplayName("módulo com aulas não é excluído")
    void naoExcluiComAulas() {
        when(modulos.findById(10L)).thenReturn(Optional.of(modulo(10, turma, 1)));
        when(aulas.existsByModuloId(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.excluir(10L))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("O módulo tem aulas. Exclua as aulas ou inative o módulo.");
        verify(modulos, never()).delete(any());
    }

    @Test
    @DisplayName("renomeia e inativa")
    void renomearEInativar() {
        Modulo modulo = modulo(10, turma, 1);
        when(modulos.findById(10L)).thenReturn(Optional.of(modulo));

        service.renomear(10L, "Novo");
        service.alterarStatus(10L, StatusConteudo.INATIVO);

        assertThat(modulo.getTitulo()).isEqualTo("Novo");
        assertThat(modulo.getStatus()).isEqualTo(StatusConteudo.INATIVO);
    }

    @Test
    @DisplayName("módulo inexistente responde não encontrado")
    void inexistente() {
        when(modulos.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renomear(9L, "Novo"))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Módulo não encontrado.");
    }
}
