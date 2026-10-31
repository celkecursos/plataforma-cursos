package br.com.celke.cursos.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.Mailpit;
import br.com.celke.cursos.usuario.domain.Papel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Direitos do titular: a cópia dos dados em JSON e a exclusão da conta (senha e confirmação, aviso por
 * e-mail, conta anonimizada, sessões encerradas, registros com prazo legal mantidos).
 */
class PrivacidadeIT extends IntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private Mailpit mailpit;

    private MvcTestResult excluir(String token, String senha, String confirmacao) {
        return mvc.post()
                .uri("/api/perfil/exclusao")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senha\":\"%s\",\"confirmacao\":\"%s\"}".formatted(senha, confirmacao))
                .exchange();
    }

    @Test
    @DisplayName("baixar meus dados: JSON em anexo com a conta e cada seção; sem login, 401")
    void exportar() {
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);
        String token = login(aluno.email(), aluno.senha());

        assertThat(mvc.get().uri("/api/perfil/dados")).hasStatus(HttpStatus.UNAUTHORIZED);
        MvcTestResult dados = mvc.get()
                .uri("/api/perfil/dados")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange();
        assertThat(dados)
                .hasStatusOk()
                .hasHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"meus-dados.json\"");
        assertThat(dados).bodyJson().extractingPath("$.conta.email").isEqualTo(aluno.email());
        assertThat(dados).bodyJson().extractingPath("$.entradas[0].resultado").isEqualTo("SUCESSO");
        for (String secao : new String[] {
            "matriculas", "progresso", "forum", "certificados", "pedidos", "aceitesTermos", "acessosVideo"
        }) {
            assertThat(dados).bodyJson().hasPath("$." + secao);
        }
    }

    @Test
    @DisplayName("excluir: senha errada ou sem a confirmação é recusado; certo anonimiza, encerra e avisa")
    void excluirConta() {
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);
        String token = login(aluno.email(), aluno.senha());

        assertThat(excluir(token, "senha-errada-qualquer", "EXCLUIR")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(excluir(token, aluno.senha(), "excluir")).hasStatus(HttpStatus.BAD_REQUEST);

        MvcTestResult excluida = excluir(token, aluno.senha(), "EXCLUIR");
        assertThat(excluida).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(excluida.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");

        // A sessão cai na hora e a conta não entra mais
        assertThat(mvc.get().uri("/api/perfil").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.post()
                        .uri("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(aluno.email(), aluno.senha())))
                .hasStatus(HttpStatus.UNAUTHORIZED);

        var conta = jdbc.sql("SELECT nome, email, senha_hash, ativo FROM usuarios WHERE id = ?")
                .param(aluno.id())
                .query()
                .singleRow();
        assertThat(conta.get("nome")).isEqualTo("Conta excluída");
        assertThat(conta.get("email")).isEqualTo("excluida-" + aluno.id() + "@conta-excluida.invalid");
        assertThat(conta.get("senha_hash")).isNull();
        assertThat(conta.get("ativo")).isEqualTo(false);
        assertThat(jdbc.sql("SELECT count(*) FROM refresh_tokens WHERE usuario_id = ?")
                        .param(aluno.id())
                        .query(Long.class)
                        .single())
                .isZero();
        // A auditoria de login fica pelo prazo legal
        assertThat(jdbc.sql("SELECT count(*) FROM auditoria_login WHERE usuario_id = ?")
                        .param(aluno.id())
                        .query(Long.class)
                        .single())
                .isPositive();

        processarFila();
        assertThat(mailpit.aguardarEmailPara(aluno.email()).Subject()).isEqualTo("Sua conta foi excluída");
    }

    @Test
    @DisplayName("administrador não exclui a própria conta por aqui")
    void administrador() {
        UsuarioTeste admin = criarUsuario(Papel.ADMIN);

        assertThat(excluir(login(admin.email(), admin.senha()), admin.senha(), "EXCLUIR"))
                .hasStatus(HttpStatus.CONFLICT);
    }
}
