package br.com.celke.cursos.shared.erro;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MuitasTentativasExceptionTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "zero vira 1 minuto,        0,    'Muitas tentativas. Tente novamente em 1 minuto.'",
        "1 segundo vira 1 minuto,   1,    'Muitas tentativas. Tente novamente em 1 minuto.'",
        "exatamente 60 segundos,    60,   'Muitas tentativas. Tente novamente em 1 minuto.'",
        "61 segundos arredonda,     61,   'Muitas tentativas. Tente novamente em 2 minutos.'",
        "15 minutos,                900,  'Muitas tentativas. Tente novamente em 15 minutos.'",
    })
    void mensagemEmMinutosArredondadaParaCima(String caso, long segundos, String esperada) {
        assertThat(new MuitasTentativasException(Duration.ofSeconds(segundos)).getMessage())
                .isEqualTo(esperada);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "zero vira 1 segundo,            0,    1",
        "1 milissegundo vira 1 segundo,  1,    1",
        "exatamente 1 segundo,           1000, 1",
        "1001 ms arredonda para 2,       1001, 2",
    })
    void retryAfterEmSegundosArredondadoParaCima(String caso, long milissegundos, long esperado) {
        assertThat(new MuitasTentativasException(Duration.ofMillis(milissegundos)).segundosDeEspera())
                .isEqualTo(esperado);
    }
}
