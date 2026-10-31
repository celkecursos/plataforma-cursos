package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.Mailpit;
import br.com.celke.cursos.usuario.domain.Papel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: cadastro → e-mail de confirmação → confirmação → login; mesma resposta para e-mail novo e já
 * cadastrado; reenvio do link; link de uso único; limite de e-mails por hora.
 * Não cobre: regras caso a caso (CadastroServiceTest, TokenUsoUnicoServiceTest).
 * Dado criado: contas it-<stamp>@teste.celke.uno e os e-mails delas no Mailpit.
 */
class CadastroIT extends IntegrationTest {

    private static final String SENHA = "senha-do-cadastro";
    private static final String MENSAGEM = "Cadastro recebido. Confira o seu e-mail para continuar.";

    @Autowired
    private Mailpit mailpit;

    @Autowired
    private JdbcClient jdbc;

    @Test
    @DisplayName("cadastro, confirmação pelo link do e-mail e login")
    void jornadaCompleta() {
        String email = stamp() + "@teste.celke.uno";

        assertThat(cadastrar("Ana Souza", email, SENHA))
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson()
                .extractingPath("$.mensagem")
                .isEqualTo(MENSAGEM);
        processarFila();

        Mailpit.EmailRecebido recebido = mailpit.aguardarEmailPara(email);
        assertThat(recebido.Subject()).isEqualTo("Confirme o seu e-mail");
        assertThat(recebido.HTML())
                .contains("Olá, <span>Ana Souza</span>!")
                .contains("http://localhost:5173/confirmar-email?token=")
                .contains("48 horas");

        // Antes de confirmar, a senha certa recebe a orientação de confirmar o e-mail
        assertThat(tentarLogin(email, SENHA))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Confirme o seu e-mail antes de entrar. Se não recebeu o link, peça um novo.");

        assertThat(confirmar(Mailpit.tokenDoLink(recebido)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.mensagem")
                .isEqualTo("E-mail confirmado. Você já pode entrar.");

        assertThat(entrar(email, SENHA))
                .bodyJson()
                .extractingPath("$.usuario.papeis")
                .asArray()
                .containsExactly("ALUNO");
    }

    @Test
    @DisplayName("o link de confirmação vale uma vez só")
    void linkDeUsoUnico() {
        String email = stamp() + "@teste.celke.uno";
        cadastrar("Ana", email, SENHA);
        processarFila();
        String token = Mailpit.tokenDoLink(mailpit.aguardarEmailPara(email));

        assertThat(confirmar(token)).hasStatusOk();
        assertLinkInvalido(confirmar(token));
    }

    @Test
    @DisplayName("e-mail já cadastrado recebe a mesma resposta, a conta não muda e o dono é avisado")
    void emailJaCadastrado() {
        UsuarioTeste existente = criarUsuario(Papel.ALUNO);

        assertThat(cadastrar("Outra Pessoa", existente.email(), "girassol-azul-77"))
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson()
                .extractingPath("$.mensagem")
                .isEqualTo(MENSAGEM);
        processarFila();

        assertThat(mailpit.aguardarEmailPara(existente.email()).Subject())
                .isEqualTo("Tentativa de cadastro com o seu e-mail");
        assertThat(contasCom(existente.email())).isEqualTo(1);
        entrar(existente.email(), existente.senha());
        assertThat(tentarLogin(existente.email(), "girassol-azul-77")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("reenviar confirmação manda um link novo e invalida o anterior")
    void reenviarInvalidaAnterior() {
        String email = stamp() + "@teste.celke.uno";
        cadastrar("Ana", email, SENHA);
        processarFila();
        String primeiro = Mailpit.tokenDoLink(mailpit.aguardarEmailPara(email));

        assertThat(reenviar(email)).hasStatus(HttpStatus.ACCEPTED);
        processarFila();
        String segundo = Mailpit.tokenDoLink(mailpit.aguardarEmailPara(email));

        assertThat(segundo).isNotEqualTo(primeiro);
        assertLinkInvalido(confirmar(primeiro));
        assertThat(confirmar(segundo)).hasStatusOk();
    }

    @Test
    @DisplayName("reenviar para e-mail sem cadastro responde igual e não envia nada")
    void reenviarSemCadastro() {
        String email = stamp() + "@teste.celke.uno";

        assertThat(reenviar(email))
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson()
                .extractingPath("$.mensagem")
                .isEqualTo("Se houver um cadastro pendente para este e-mail, enviaremos um novo link.");
        processarFila();

        assertThat(mailpit.quantidadePara(email)).isZero();
    }

    @Test
    @DisplayName("no máximo 3 e-mails de confirmação por hora para a mesma conta")
    void limiteDeEmailsPorHora() {
        String email = stamp() + "@teste.celke.uno";
        cadastrar("Ana", email, SENHA);
        for (int i = 0; i < 3; i++) {
            assertThat(reenviar(email)).hasStatus(HttpStatus.ACCEPTED);
        }
        processarFila();

        assertThat(mailpit.quantidadePara(email)).isEqualTo(3);
    }

    @Test
    @DisplayName("senha curta é recusada com o campo indicado")
    void senhaCurta() {
        assertThat(cadastrar("Ana", stamp() + "@teste.celke.uno", "curta"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .asArray()
                .containsExactly("senha");
    }

    @Test
    @DisplayName("token desconhecido é recusado")
    void tokenDesconhecido() {
        assertLinkInvalido(confirmar("token-que-nunca-existiu"));
    }

    private MvcTestResult cadastrar(String nome, String email, String senha) {
        return post(
                "/api/auth/cadastro",
                "{\"nome\":\"%s\",\"email\":\"%s\",\"senha\":\"%s\"}".formatted(nome, email, senha));
    }

    private MvcTestResult confirmar(String token) {
        return post("/api/auth/confirmar-email", "{\"token\":\"%s\"}".formatted(token));
    }

    private MvcTestResult reenviar(String email) {
        return post("/api/auth/reenviar-confirmacao", "{\"email\":\"%s\"}".formatted(email));
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

    private int contasCom(String email) {
        return jdbc.sql("SELECT COUNT(*) FROM usuarios WHERE email = :email")
                .param("email", email)
                .query(Integer.class)
                .single();
    }

    private static void assertLinkInvalido(MvcTestResult resposta) {
        assertThat(resposta)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Link inválido ou expirado. Peça um novo.");
    }
}
