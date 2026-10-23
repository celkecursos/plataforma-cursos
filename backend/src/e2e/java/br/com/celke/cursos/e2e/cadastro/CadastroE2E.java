package br.com.celke.cursos.e2e.cadastro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import br.com.celke.cursos.e2e.support.Api;
import br.com.celke.cursos.e2e.support.Dados;
import br.com.celke.cursos.e2e.support.Mailpit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Jornada: cadastro pelo Caddy → e-mail enviado pelo worker de verdade → confirmação pelo link →
 * login. Alcança o que os testes de integração não alcançam: o proxy, a fila consumida pelo worker
 * e o SMTP real (Mailpit).
 */
class CadastroE2E {

    private static final String SENHA = "senha-do-cadastro-e2e";

    @Test
    @DisplayName("aluno se cadastra, recebe o e-mail do worker, confirma pelo link e entra")
    void cadastroConfirmacaoLogin() {
        String email = Dados.email();

        // 1. Cadastro pela área do aluno
        Api.aluno()
                .body("{\"nome\":\"Aluna e2e\",\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, SENHA))
                .post("/api/auth/cadastro")
                .then()
                .statusCode(202)
                .body("mensagem", equalTo("Cadastro recebido. Confira o seu e-mail para continuar."));

        // 2. O worker consome a fila e entrega o e-mail com o link para a área do aluno
        Mailpit.EmailRecebido recebido = Mailpit.aguardar(email, "Confirme o seu e-mail");
        assertThat(recebido.remetente()).isEqualTo("nao-responda@teste.celke.uno");
        assertThat(recebido.html()).contains("http://aluno.localhost:8088/confirmar-email?token=");

        // 3. Antes de confirmar, a senha certa recebe a orientação de confirmar
        Api.aluno()
                .body("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, SENHA))
                .post("/api/auth/login")
                .then()
                .statusCode(403);

        // 4. Confirmação pelo token do link e login
        Api.aluno()
                .body("{\"token\":\"%s\"}".formatted(Mailpit.token(recebido)))
                .post("/api/auth/confirmar-email")
                .then()
                .statusCode(200);
        assertThat(Api.login(email, SENHA).jsonPath().getList("usuario.papeis")).containsExactly("ALUNO");
    }
}
