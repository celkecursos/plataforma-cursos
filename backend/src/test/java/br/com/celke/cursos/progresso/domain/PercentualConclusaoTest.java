package br.com.celke.cursos.progresso.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PercentualConclusaoTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "turma sem aulas,                0, 0,   0",
        "nenhuma aula vista,             0, 10,  0",
        "todas as aulas vistas,          10, 10, 100",
        "uma de uma,                     1, 1,   100",
        "metade,                         5, 10,  50",
        "um terço arredonda para baixo,  1, 3,   33",
        "dois terços arredonda para baixo, 2, 3, 66",
        "falta uma de 200 não chega a 100, 199, 200, 99",
        "uma de 1000 fica em 0,          1, 1000, 0",
    })
    @DisplayName("percentual de aulas vistas, arredondado para baixo")
    void percentual(String caso, int vistas, int total, int esperado) {
        assertThat(PercentualConclusao.de(vistas, total)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "{0} de {1}")
    @CsvSource({"-1, 10", "1, -1", "11, 10", "1, 0"})
    @DisplayName("contagem negativa ou com mais vistas que aulas é recusada")
    void contagemInvalida(int vistas, int total) {
        assertThatThrownBy(() -> PercentualConclusao.de(vistas, total))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Contagem inválida: %d de %d.".formatted(vistas, total));
    }
}
