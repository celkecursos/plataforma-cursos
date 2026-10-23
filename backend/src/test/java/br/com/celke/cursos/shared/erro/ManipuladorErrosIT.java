package br.com.celke.cursos.shared.erro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import br.com.celke.cursos.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Cobre: formato ProblemDetail de todos os erros da API (validação, negócio, MVC, inesperado,
 * 401 e 403), sem vazar detalhes internos.
 * Não cobre: regras de cada módulo (testes dos módulos).
 * Dado criado: nenhum.
 */
class ManipuladorErrosIT extends IntegrationTest {

    private static final RequestPostProcessor ALUNO = user("aluno").roles("ALUNO");

    @Test
    @DisplayName("validação devolve 400 com a lista de campos inválidos em português")
    void validacao() {
        assertThat(mvc.post()
                        .uri("/teste/erros/validacao")
                        .with(ALUNO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\" \",\"email\":\"invalido\",\"quantidade\":0}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "status": 400,
                          "detail": "Dados inválidos.",
                          "instance": "/teste/erros/validacao",
                          "erros": [
                            {"campo": "email", "mensagem": "deve ser um endereço de e-mail bem formado"},
                            {"campo": "nome", "mensagem": "não deve estar em branco"},
                            {"campo": "quantidade", "mensagem": "deve ser maior que ou igual à 1"}
                          ]
                        }
                        """);
    }

    @Test
    @DisplayName("JSON malformado devolve 400 sem detalhes do parser")
    void jsonMalformado() {
        assertThat(mvc.post()
                        .uri("/teste/erros/validacao")
                        .with(ALUNO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O corpo da requisição é inválido.");
    }

    @Test
    @DisplayName("recurso não encontrado devolve 404 com a mensagem do negócio")
    void naoEncontrado() {
        assertThat(mvc.get().uri("/teste/erros/nao-encontrado").with(ALUNO))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"status": 404, "detail": "Curso não encontrado.", "instance": "/teste/erros/nao-encontrado"}
                        """);
    }

    @Test
    @DisplayName("conflito devolve 409 com a mensagem do negócio")
    void conflito() {
        assertThat(mvc.get().uri("/teste/erros/conflito").with(ALUNO))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Já existe um curso com este slug.");
    }

    @Test
    @DisplayName("erro inesperado devolve 500 sem a mensagem interna, só o id da requisição")
    void inesperado() throws Exception {
        var resposta = mvc.get()
                .uri("/teste/erros/inesperado")
                .with(ALUNO)
                .header("X-Request-Id", "id-do-teste")
                .exchange();
        assertThat(resposta).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resposta)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Erro interno. Se o problema continuar, informe o código id-do-teste.");
        assertThat(resposta.getResponse().getContentAsString()).doesNotContain("detalhe interno", "IllegalState");
    }

    @Test
    @DisplayName("rota inexistente devolve 404 em ProblemDetail")
    void rotaInexistente() {
        assertThat(mvc.get().uri("/teste/nao-existe").with(ALUNO))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Recurso não encontrado.");
    }

    @Test
    @DisplayName("método não aceito devolve 405 em ProblemDetail")
    void metodoNaoAceito() {
        assertThat(mvc.delete().uri("/teste/erros/conflito").with(ALUNO))
                .hasStatus(HttpStatus.METHOD_NOT_ALLOWED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O método DELETE não é aceito nesta rota.");
    }

    @Test
    @DisplayName("sem autenticação devolve 401 em ProblemDetail")
    void semAutenticacao() {
        assertThat(mvc.get().uri("/teste/erros/conflito").header("X-Request-Id", "id-401"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "status": 401,
                          "detail": "Autenticação necessária.",
                          "instance": "/teste/erros/conflito",
                          "requestId": "id-401"
                        }
                        """);
    }

    @Test
    @DisplayName("sem o papel exigido devolve 403 em ProblemDetail")
    void semPapel() {
        assertThat(mvc.get().uri("/teste/erros/admin").with(ALUNO))
                .hasStatus(HttpStatus.FORBIDDEN)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"status": 403, "detail": "Acesso negado.", "instance": "/teste/erros/admin"}
                        """);
    }

    @Test
    @DisplayName("com o papel exigido a rota responde normalmente")
    void comPapel() {
        assertThat(mvc.get().uri("/teste/erros/admin").with(user("admin").roles("ADMIN")))
                .hasStatusOk()
                .hasBodyTextEqualTo("ok");
    }
}
