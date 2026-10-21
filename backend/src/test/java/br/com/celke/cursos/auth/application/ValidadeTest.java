package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ValidadeTest {

    @ParameterizedTest(name = "{0} minutos")
    @CsvSource({
        "1,    1 minuto",
        "30,   30 minutos",
        "60,   1 hora",
        "90,   90 minutos",
        "120,  2 horas",
        "2880, 48 horas",
    })
    void texto(long minutos, String esperado) {
        assertThat(Validade.texto(Duration.ofMinutes(minutos))).isEqualTo(esperado);
    }
}
