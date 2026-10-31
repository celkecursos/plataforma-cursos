package br.com.celke.cursos.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LoginRequestTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> camposInvalidos(LoginRequest request) {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("payload válido não tem erros")
    void payloadValido() {
        assertThat(camposInvalidos(new LoginRequest("ana@celke.uno", "senha"))).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "e-mail vazio,            '',            senha, email",
        "e-mail sem formato,      ana.celke.uno, senha, email",
        "senha vazia,             ana@celke.uno, '',    senha",
    })
    void camposObrigatorios(String caso, String email, String senha, String campo) {
        assertThat(camposInvalidos(new LoginRequest(email, senha))).containsExactly(campo);
    }

    @Test
    @DisplayName("senha acima de 128 caracteres é recusada antes de calcular o hash")
    void senhaMuitoLonga() {
        assertThat(camposInvalidos(new LoginRequest("ana@celke.uno", "a".repeat(129))))
                .containsExactly("senha");
        assertThat(camposInvalidos(new LoginRequest("ana@celke.uno", "a".repeat(128))))
                .isEmpty();
    }
}
