package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.Mailpit;
import br.com.celke.cursos.usuario.domain.Papel;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: pedido de recuperação → e-mail com link → nova senha → sessões antigas encerradas; mesma
 * resposta para e-mail sem conta; link de uso único; e-mail confirmado pela redefinição.
 * Não cobre: regras caso a caso (RecuperacaoSenhaServiceTest, TokenUsoUnicoServiceTest).
 * Dado criado: contas it-<stamp>@teste.celke.uno e os e-mails delas no Mailpit.
 */
class RecuperacaoSenhaIT extends IntegrationTest {

    private static final String NOVA_SENHA = "minha-nova-senha";
    private static final String MENSAGEM =
            "Se houver uma conta com este e-mail, enviaremos um link para criar uma nova senha.";

    @Autowired
    private Mailpit mailpit;

    @Test
    @DisplayName("nova senha pelo link do e-mail: a antiga deixa de valer e as sessões abertas são encerradas")
    void jornadaCompleta() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        String cookieAntigo = entrar(usuario.email(), usuario.senha())
                .getResponse()
                .getCookie("refresh_token")
                .getValue();

        assertThat(solicitar(usuario.email()))
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson()
                .extractingPath("$.mensagem")
                .isEqualTo(MENSAGEM);
        processarFila();
        Mailpit.EmailRecebido recebido = mailpit.aguardarEmailPara(usuario.email());
        assertThat(recebido.Subject()).isEqualTo("Crie uma nova senha");
        assertThat(recebido.HTML())
                .contains("http://localhost:5173/redefinir-senha?token=")
                .contains("1 hora");

        assertThat(redefinir(Mailpit.tokenDoLink(recebido), NOVA_SENHA))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.mensagem")
                .isEqualTo("Senha alterada. Entre com a nova senha.");

        assertThat(tentarLogin(usuario.email(), usuario.senha())).hasStatus(HttpStatus.UNAUTHORIZED);
        entrar(usuario.email(), NOVA_SENHA);
        assertThat(mvc.post().uri("/api/auth/refresh").cookie(new Cookie("refresh_token", cookieAntigo)))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("o link de recuperação vale uma vez só")
    void linkDeUsoUnico() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        solicitar(usuario.email());
        processarFila();
        String token = Mailpit.tokenDoLink(mailpit.aguardarEmailPara(usuario.email()));

        assertThat(redefinir(token, NOVA_SENHA)).hasStatusOk();
        assertThat(redefinir(token, "outra-senha-nova"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Link inválido ou expirado. Peça um novo.");
        entrar(usuario.email(), NOVA_SENHA);
    }

    @Test
    @DisplayName("e-mail sem conta recebe a mesma resposta e nenhum e-mail é enviado")
    void emailSemConta() {
        String email = stamp() + "@teste.celke.uno";

        assertThat(solicitar(email))
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson()
                .extractingPath("$.mensagem")
                .isEqualTo(MENSAGEM);
        processarFila();

        assertThat(mailpit.quantidadePara(email)).isZero();
    }

    @Test
    @DisplayName("redefinir a senha também confirma o e-mail de um cadastro pendente")
    void confirmaEmailPendente() {
        String email = stamp() + "@teste.celke.uno";
        post(
                "/api/auth/cadastro",
                "{\"nome\":\"Ana\",\"email\":\"%s\",\"senha\":\"senha-do-cadastro\"}".formatted(email));
        solicitar(email);
        processarFila();
        // O e-mail mais recente é o de recuperação
        String token = Mailpit.tokenDoLink(mailpit.aguardarEmailPara(email));

        assertThat(redefinir(token, NOVA_SENHA)).hasStatusOk();

        entrar(email, NOVA_SENHA);
    }

    @Test
    @DisplayName("nova senha curta é recusada com o campo indicado")
    void senhaCurta() {
        assertThat(redefinir("qualquer", "curta"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .asArray()
                .containsExactly("senha");
    }

    private MvcTestResult solicitar(String email) {
        return post("/api/auth/recuperar-senha", "{\"email\":\"%s\"}".formatted(email));
    }

    private MvcTestResult redefinir(String token, String senha) {
        return post("/api/auth/redefinir-senha", "{\"token\":\"%s\",\"senha\":\"%s\"}".formatted(token, senha));
    }

    private MvcTestResult tentarLogin(String email, String senha) {
        return post("/api/auth/login", "{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha));
    }

    private MvcTestResult post(String rota, String json) {
        return mvc.post()
                .uri(rota)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }
}
