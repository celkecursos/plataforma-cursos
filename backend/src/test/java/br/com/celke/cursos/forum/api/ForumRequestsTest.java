package br.com.celke.cursos.forum.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.forum.api.ForumRequests.ModerarResposta;
import br.com.celke.cursos.forum.api.ForumRequests.Perguntar;
import br.com.celke.cursos.forum.api.ForumRequests.Responder;
import br.com.celke.cursos.forum.api.ForumRequests.ResponderInstrutor;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ForumRequestsTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> camposInvalidos(Object dto) {
        return VALIDATOR.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("pergunta: título até 150, corpo até 10.000, anexos opcionais")
    void perguntar() {
        assertThat(camposInvalidos(new Perguntar("Dúvida", "Corpo", null, null)))
                .isEmpty();
        assertThat(camposInvalidos(new Perguntar("t".repeat(150), "c".repeat(10_000), 9L, List.of("k"))))
                .isEmpty();
        assertThat(camposInvalidos(new Perguntar(" ", "", null, null))).containsExactlyInAnyOrder("titulo", "corpo");
        assertThat(camposInvalidos(new Perguntar("t".repeat(151), "c".repeat(10_001), null, null)))
                .containsExactlyInAnyOrder("titulo", "corpo");
        assertThat(camposInvalidos(new Perguntar("T", "C", null, Collections.nCopies(11, "k"))))
                .containsExactly("anexos");
        assertThat(camposInvalidos(new Perguntar("T", "C", null, List.of(" "))))
                .containsExactly("anexos[0].<list element>");
    }

    @Test
    @DisplayName("resposta e resposta do instrutor: corpo obrigatório")
    void responder() {
        assertThat(camposInvalidos(new Responder("ok", null))).isEmpty();
        assertThat(camposInvalidos(new Responder(" ", null))).containsExactly("corpo");
        assertThat(camposInvalidos(new ResponderInstrutor(null))).containsExactly("corpo");
    }

    @Test
    @DisplayName("moderar resposta exige o oculto")
    void moderarResposta() {
        assertThat(camposInvalidos(new ModerarResposta(null))).containsExactly("oculto");
        assertThat(camposInvalidos(new ModerarResposta(true))).isEmpty();
    }
}
