package br.com.celke.cursos.curso.application;

import static br.com.celke.cursos.support.fixtures.CursoFixture.aula;
import static br.com.celke.cursos.support.fixtures.CursoFixture.curso;
import static br.com.celke.cursos.support.fixtures.CursoFixture.modulo;
import static br.com.celke.cursos.support.fixtures.CursoFixture.turma;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.domain.Aula;
import br.com.celke.cursos.curso.domain.Modulo;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import br.com.celke.cursos.curso.infra.AulaRepository;
import br.com.celke.cursos.curso.infra.ModuloRepository;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
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
class AulaAdmServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private ModuloRepository modulos;

    @Mock
    private AulaRepository aulas;

    private AulaAdmService service;
    private final Modulo modulo = modulo(10, turma(5, curso(1, "java")), 1);

    @BeforeEach
    void setUp() {
        service = new AulaAdmService(modulos, aulas, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("aula nova entra no fim do módulo, com os textos aparados")
    void criarNoFim() {
        when(modulos.travar(10L)).thenReturn(Optional.of(modulo));
        when(aulas.findByModuloIdOrderByOrdem(10L)).thenReturn(List.of(aula(100, modulo, 1)));
        when(aulas.save(any(Aula.class))).thenAnswer(i -> i.getArgument(0));

        Aula aula = service.criar(10L, new DadosAula(" Boas-vindas ", "  ", true));

        assertThat(aula.getOrdem()).isEqualTo(2);
        assertThat(aula.getTitulo()).isEqualTo("Boas-vindas");
        assertThat(aula.getDescricao()).isNull();
        assertThat(aula.isGratuita()).isTrue();
        assertThat(aula.getStatus()).isEqualTo(StatusConteudo.ATIVO);
    }

    @Test
    @DisplayName("módulo inexistente: listar, criar e reordenar respondem não encontrado")
    void moduloInexistente() {
        when(modulos.existsById(9L)).thenReturn(false);
        when(modulos.travar(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listar(9L)).isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> service.criar(9L, new DadosAula("Aula", null, false)))
                .isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> service.reordenar(9L, List.of(1L))).isInstanceOf(NaoEncontradoException.class);
        verify(aulas, never()).save(any());
    }

    @Test
    @DisplayName("reordenar devolve as aulas na nova ordem")
    void reordenar() {
        when(modulos.travar(10L)).thenReturn(Optional.of(modulo));
        when(aulas.findByModuloIdOrderByOrdem(10L)).thenReturn(List.of(aula(100, modulo, 1), aula(200, modulo, 2)));

        assertThat(service.reordenar(10L, List.of(200L, 100L)))
                .extracting(Aula::getId)
                .containsExactly(200L, 100L);
    }

    @Test
    @DisplayName("excluir refaz a sequência das que ficam")
    void excluirCompacta() {
        Aula excluida = aula(100, modulo, 1);
        Aula restante = aula(200, modulo, 2);
        when(aulas.findById(100L)).thenReturn(Optional.of(excluida));
        when(aulas.findByModuloIdOrderByOrdem(10L)).thenReturn(List.of(restante));

        service.excluir(100L);

        verify(modulos).travar(10L);
        verify(aulas).delete(excluida);
        assertThat(restante.getOrdem()).isEqualTo(1);
    }

    @Test
    @DisplayName("atualiza os dados e o status")
    void atualizar() {
        Aula aula = aula(100, modulo, 1);
        when(aulas.findById(100L)).thenReturn(Optional.of(aula));

        service.atualizar(100L, new DadosAula("Nova", " Texto ", true));
        service.alterarStatus(100L, StatusConteudo.INATIVO);

        assertThat(aula.getTitulo()).isEqualTo("Nova");
        assertThat(aula.getDescricao()).isEqualTo("Texto");
        assertThat(aula.isGratuita()).isTrue();
        assertThat(aula.getStatus()).isEqualTo(StatusConteudo.INATIVO);
    }

    @Test
    @DisplayName("aula inexistente responde não encontrada")
    void inexistente() {
        when(aulas.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.excluir(9L))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Aula não encontrada.");
        verify(aulas, never()).delete(any());
    }
}
