package br.com.celke.cursos.arquivo.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.arquivo.api.ArquivoRequests.Confirmar;
import br.com.celke.cursos.arquivo.api.ArquivoRequests.IniciarEnvio;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArquivoRequestsTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> camposInvalidos(Object dto) {
        return VALIDATOR.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("início do envio: tipo obrigatório e tamanho positivo")
    void iniciar() {
        assertThat(camposInvalidos(new IniciarEnvio("application/zip", 1))).isEmpty();
        assertThat(camposInvalidos(new IniciarEnvio(" ", 0))).containsExactlyInAnyOrder("contentType", "tamanho");
        assertThat(camposInvalidos(new IniciarEnvio("application/zip", -1))).containsExactly("tamanho");
    }

    @Test
    @DisplayName("confirmação: chave, título e nome obrigatórios, com os limites de tamanho")
    void confirmar() {
        assertThat(camposInvalidos(new Confirmar("k", "Código-fonte", "aula.zip")))
                .isEmpty();
        assertThat(camposInvalidos(new Confirmar("", " ", null)))
                .containsExactlyInAnyOrder("chave", "titulo", "nomeArquivo");
        assertThat(camposInvalidos(new Confirmar("k".repeat(201), "t".repeat(151), "n".repeat(256))))
                .containsExactlyInAnyOrder("chave", "titulo", "nomeArquivo");
        assertThat(camposInvalidos(new Confirmar("k".repeat(200), "t".repeat(150), "n".repeat(255))))
                .isEmpty();
    }
}
