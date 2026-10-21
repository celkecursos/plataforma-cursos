package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AuthPropertiesTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "31 bytes é curto demais,  31, false",
        "exatamente 32 bytes,      32, true",
        "48 bytes,                 48, true",
    })
    void tamanhoDaChave(String caso, int bytes, boolean valido) {
        String segredo = Base64.getEncoder().encodeToString(new byte[bytes]);

        assertThat(props(segredo).isSegredoValido()).isEqualTo(valido);
    }

    @ParameterizedTest(name = "[{0}]")
    @CsvSource({"isto não é base64!, false", "'', true"})
    void formatoDaChave(String segredo, boolean valido) {
        // Vazio fica a cargo do @NotBlank; aqui só o formato inválido é acusado
        assertThat(props(segredo).isSegredoValido()).isEqualTo(valido);
    }

    private static AuthProperties props(String segredo) {
        return new AuthProperties(segredo, "plataforma-cursos", Duration.ofMinutes(10), Duration.ofDays(14), true);
    }
}
