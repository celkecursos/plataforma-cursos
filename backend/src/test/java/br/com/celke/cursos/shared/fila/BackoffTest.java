package br.com.celke.cursos.shared.fila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BackoffTest {

    // base 30 s e máximo de 1 h (3600 s)
    private final Backoff backoff = new Backoff(Duration.ofSeconds(30), Duration.ofHours(1));

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "primeira tentativa usa a base,          1,  30",
        "segunda tentativa dobra,                2,  60",
        "terceira tentativa quadruplica,         3,  120",
        "sétima tentativa ainda abaixo do máximo, 7,  1920",
        "oitava tentativa atinge o máximo,       8,  3600",
        "tentativa muito alta fica no máximo,    100, 3600",
        "tentativa no limite do int fica no máximo, 2147483647, 3600",
    })
    void atrasoPorTentativa(String caso, int tentativa, long segundosEsperados) {
        assertThat(backoff.atraso(tentativa)).isEqualTo(Duration.ofSeconds(segundosEsperados));
    }

    @Test
    @DisplayName("máximo igual à base devolve sempre a base")
    void maximoIgualBase() {
        Backoff fixo = new Backoff(Duration.ofSeconds(10), Duration.ofSeconds(10));
        assertThat(fixo.atraso(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(fixo.atraso(5)).isEqualTo(Duration.ofSeconds(10));
    }

    @ParameterizedTest(name = "tentativa {0}")
    @CsvSource({"0", "-1"})
    @DisplayName("rejeita tentativa menor que 1")
    void rejeitaTentativaInvalida(int tentativa) {
        assertThatThrownBy(() -> backoff.atraso(tentativa))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A tentativa começa em 1.");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "base zero,            0,  60",
        "base negativa,        -1, 60",
        "máximo menor que base, 60, 30",
    })
    void rejeitaConfiguracaoInvalida(String caso, long baseSegundos, long maximoSegundos) {
        assertThatThrownBy(() -> new Backoff(Duration.ofSeconds(baseSegundos), Duration.ofSeconds(maximoSegundos)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Backoff exige base positiva e máximo maior ou igual à base.");
    }
}
