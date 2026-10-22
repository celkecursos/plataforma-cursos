package br.com.celke.cursos.progresso.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.progresso.api.ProgressoRequests.Atualizar;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProgressoRequestsTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> camposInvalidos(Object dto) {
        return VALIDATOR.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("qualquer um dos campos sozinho basta")
    void umCampoBasta() {
        assertThat(camposInvalidos(new Atualizar(10, null, null))).isEmpty();
        assertThat(camposInvalidos(new Atualizar(null, false, null))).isEmpty();
        assertThat(camposInvalidos(new Atualizar(null, null, true))).isEmpty();
    }

    @Test
    @DisplayName("corpo sem nenhum campo é recusado")
    void nenhumCampo() {
        assertThat(camposInvalidos(new Atualizar(null, null, null))).containsExactly("algumCampo");
    }

    @ParameterizedTest(name = "posição {0} → válida: {1}")
    @CsvSource({"-1, false", "0, true", "86400, true", "86401, false"})
    @DisplayName("posição de 0 a 24 horas")
    void posicao(int posicao, boolean valida) {
        assertThat(camposInvalidos(new Atualizar(posicao, null, null)))
                .isEqualTo(valida ? Set.of() : Set.of("posicaoSegundos"));
    }
}
