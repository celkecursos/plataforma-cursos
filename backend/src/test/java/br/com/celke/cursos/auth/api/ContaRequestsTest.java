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

class ContaRequestsTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> camposInvalidos(Object request) {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static ContaRequests.Cadastro cadastroValido() {
        return new ContaRequests.Cadastro("Ana", "ana@celke.uno", "senha-ok-10");
    }

    @Test
    @DisplayName("cadastro válido não tem erros")
    void cadastroValidoSemErros() {
        assertThat(camposInvalidos(cadastroValido())).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "nove caracteres é curto demais,  9,   senha",
        "dez caracteres é o mínimo,       10,  ",
        "128 caracteres é o máximo,       128, ",
        "129 caracteres passa do máximo,  129, senha",
    })
    void tamanhoDaSenha(String caso, int tamanho, String campoInvalido) {
        var request = new ContaRequests.Cadastro("Ana", "ana@celke.uno", "a".repeat(tamanho));
        Set<String> invalidos = camposInvalidos(request);
        if (campoInvalido == null) {
            assertThat(invalidos).isEmpty();
        } else {
            assertThat(invalidos).containsExactly(campoInvalido);
        }
    }

    @Test
    @DisplayName("cadastro exige nome até 120 caracteres e e-mail válido")
    void nomeEEmail() {
        assertThat(camposInvalidos(new ContaRequests.Cadastro(" ", "ana@celke.uno", "senha-ok-10")))
                .containsExactly("nome");
        assertThat(camposInvalidos(new ContaRequests.Cadastro("a".repeat(121), "ana@celke.uno", "senha-ok-10")))
                .containsExactly("nome");
        assertThat(camposInvalidos(new ContaRequests.Cadastro("a".repeat(120), "ana@celke.uno", "senha-ok-10")))
                .isEmpty();
        assertThat(camposInvalidos(new ContaRequests.Cadastro("Ana", "ana.celke.uno", "senha-ok-10")))
                .containsExactly("email");
    }

    @Test
    @DisplayName("pedidos por e-mail, token e nova senha validam os campos")
    void outrosPedidos() {
        assertThat(camposInvalidos(new ContaRequests.EmailInformado("invalido")))
                .containsExactly("email");
        assertThat(camposInvalidos(new ContaRequests.Token(" "))).containsExactly("token");
        assertThat(camposInvalidos(new ContaRequests.Token("a".repeat(101)))).containsExactly("token");
        assertThat(camposInvalidos(new ContaRequests.RedefinirSenha("abc", "curta")))
                .containsExactly("senha");
        assertThat(camposInvalidos(new ContaRequests.RedefinirSenha("abc", "senha-ok-10")))
                .isEmpty();
    }
}
