package br.com.celke.cursos.curso.domain;

import static br.com.celke.cursos.support.fixtures.CursoFixture.curso;
import static br.com.celke.cursos.support.fixtures.CursoFixture.modulo;
import static br.com.celke.cursos.support.fixtures.CursoFixture.turma;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

class OrdenacaoTest {

    private final Turma turma = turma(1, curso(1, "curso"));

    /** Módulos com ids 10, 20, 30 nas posições 1, 2, 3. */
    private List<Modulo> tresModulos() {
        return List.of(modulo(10, turma, 1), modulo(20, turma, 2), modulo(30, turma, 3));
    }

    private static List<Long> idsPelaOrdem(List<Modulo> modulos) {
        return modulos.stream()
                .sorted((a, b) -> Integer.compare(a.getOrdem(), b.getOrdem()))
                .map(Modulo::getId)
                .toList();
    }

    @Nested
    class Reordenar {

        @Test
        @DisplayName("aplica a nova ordem a partir da posição 1")
        void aplicaNovaOrdem() {
            List<Modulo> modulos = tresModulos();
            Ordenacao.reordenar(modulos, List.of(30L, 10L, 20L));
            assertThat(idsPelaOrdem(modulos)).containsExactly(30L, 10L, 20L);
            assertThat(modulos).extracting(Modulo::getOrdem).containsExactly(2, 3, 1);
        }

        static Stream<Arguments> listasInvalidas() {
            return Stream.of(
                    Arguments.of("faltando um item", List.of(10L, 20L)),
                    Arguments.of("com item a mais", List.of(10L, 20L, 30L, 40L)),
                    Arguments.of("com item repetido", List.of(10L, 10L, 20L)),
                    Arguments.of("com item de outro pai", List.of(10L, 20L, 99L)),
                    Arguments.of("vazia", List.of()));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("listasInvalidas")
        void recusaListaQueNaoTemExatamenteOsItens(String caso, List<Long> ids) {
            List<Modulo> modulos = tresModulos();
            assertThatThrownBy(() -> Ordenacao.reordenar(modulos, ids))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("A nova ordem deve ter todos os itens atuais, sem repetir nenhum.")
                    .extracting("status")
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(modulos).extracting(Modulo::getOrdem).containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("pai sem itens aceita a lista vazia")
        void paiVazio() {
            Ordenacao.reordenar(List.of(), List.of());
        }
    }

    @Test
    @DisplayName("compactar refaz 1, 2, 3 mantendo a ordem relativa")
    void compactar() {
        List<Modulo> restantes = List.of(modulo(30, turma, 5), modulo(10, turma, 1), modulo(20, turma, 3));
        Ordenacao.compactar(restantes);
        assertThat(idsPelaOrdem(restantes)).containsExactly(10L, 20L, 30L);
        assertThat(restantes).extracting(Modulo::getOrdem).containsExactly(3, 1, 2);
    }

    @Test
    @DisplayName("próxima posição vem depois da maior; lista vazia começa em 1")
    void proxima() {
        assertThat(Ordenacao.proxima(List.of())).isEqualTo(1);
        assertThat(Ordenacao.proxima(List.of(modulo(10, turma, 1), modulo(20, turma, 4))))
                .isEqualTo(5);
    }
}
