package br.com.celke.cursos.matricula.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.matricula.domain.FormaPagamento;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MatriculaRequestsTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> camposInvalidos(Object request) {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("matricular exige e-mail válido e forma de pagamento; datas e transação são opcionais")
    void matricular() {
        assertThat(camposInvalidos(
                        new MatriculaRequests.Matricular("ana@celke.uno", null, null, FormaPagamento.MANUAL, null)))
                .isEmpty();
        assertThat(camposInvalidos(new MatriculaRequests.Matricular("ana", null, null, null, "a".repeat(101))))
                .containsExactlyInAnyOrder("email", "formaPagamento", "transacao");
        assertThat(camposInvalidos(
                        new MatriculaRequests.Matricular(" ", null, null, FormaPagamento.PIX, "a".repeat(100))))
                .containsExactly("email");
    }

    @Test
    @DisplayName("período exige o início; o fim é opcional")
    void periodo() {
        assertThat(camposInvalidos(new MatriculaRequests.Periodo(Instant.EPOCH, null)))
                .isEmpty();
        assertThat(camposInvalidos(new MatriculaRequests.Periodo(null, Instant.EPOCH)))
                .containsExactly("inicio");
    }

    @Test
    @DisplayName("os dados repassam todos os campos")
    void dados() {
        var dados = new MatriculaRequests.Matricular("ana@celke.uno", Instant.EPOCH, null, FormaPagamento.PIX, "tx")
                .dados();
        assertThat(dados.email()).isEqualTo("ana@celke.uno");
        assertThat(dados.inicio()).isEqualTo(Instant.EPOCH);
        assertThat(dados.transacao()).isEqualTo("tx");
    }
}
