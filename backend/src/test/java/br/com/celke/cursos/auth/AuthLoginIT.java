package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.application.CriarAdminService;
import br.com.celke.cursos.usuario.domain.Papel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: login (corpo, cookie de refresh, mensagens iguais para e-mail inexistente e senha errada),
 * validação, /api/auth/me, admin criado pelo serviço do comando e hash legado regravado em Argon2.
 * Não cobre: validade e assinatura do JWT (AuthTokenIT) e renovação (AuthRefreshIT).
 * Dado criado: usuários it-<stamp>@teste.celke.uno.
 */
class AuthLoginIT extends IntegrationTest {

    @Autowired
    private CriarAdminService criarAdminService;

    @Autowired
    private JdbcClient jdbc;

    @Test
    @DisplayName("login devolve access token, dados públicos do usuário e o cookie de refresh")
    void loginComSucesso() throws Exception {
        MvcTestResult resposta = entrar(UsuariosSeed.ALUNO_EMAIL, UsuariosSeed.SENHA);

        assertThat(resposta).bodyJson().isLenientlyEqualTo("""
                        {
                          "tipo": "Bearer",
                          "usuario": {"email": "aluno.seed@teste.celke.uno", "nome": "Aluno do Seed", "papeis": ["ALUNO"]}
                        }
                        """);
        assertThat(resposta)
                .bodyJson()
                .extractingPath("$.accessToken")
                .asString()
                .isNotBlank();
        assertThat(resposta).bodyJson().extractingPath("$.expiraEm").asString().isNotBlank();
        String corpo = resposta.getResponse().getContentAsString();
        assertThat(corpo).doesNotContain("senha", "hash", "refresh");

        String cookie = resposta.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie)
                .startsWith("refresh_token=")
                .contains("Path=/api/auth", "HttpOnly", "Secure", "SameSite=Strict", "Max-Age=1209600")
                .doesNotContainIgnoringCase("Domain=");
    }

    @Test
    @DisplayName("e-mail é aceito com letras maiúsculas")
    void emailComMaiusculas() {
        entrar("Aluno.Seed@Teste.Celke.UNO", UsuariosSeed.SENHA);
    }

    @Test
    @DisplayName("senha errada e e-mail inexistente recebem exatamente a mesma resposta")
    void credenciaisInvalidas() throws Exception {
        MvcTestResult senhaErrada = tentarLogin(UsuariosSeed.ALUNO_EMAIL, "senha-errada");
        MvcTestResult emailInexistente = tentarLogin(stamp() + "@teste.celke.uno", "senha-errada");

        for (MvcTestResult resposta : new MvcTestResult[] {senhaErrada, emailInexistente}) {
            assertThat(resposta)
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson()
                    .extractingPath("$.detail")
                    .isEqualTo("E-mail ou senha inválidos.");
            assertThat(resposta.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        }
    }

    @Test
    @DisplayName("payload inválido devolve 400 com os campos")
    void validacao() {
        assertThat(mvc.post()
                        .uri("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nao-e-email\",\"senha\":\"\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .asArray()
                .containsExactlyInAnyOrder("email", "senha");
    }

    @Test
    @DisplayName("/api/auth/me devolve o usuário do token, sem dados sensíveis")
    void me() throws Exception {
        String token = login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA);

        MvcTestResult resposta = mvc.get()
                .uri("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange();

        assertThat(resposta).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        {"email": "admin.seed@teste.celke.uno", "papeis": ["ADMIN"]}
                        """);
        assertThat(resposta.getResponse().getContentAsString()).doesNotContain("senha", "hash");
    }

    @Test
    @DisplayName("admin criado pelo serviço do comando criar-admin entra com o papel ADMIN")
    void adminCriadoPeloComando() {
        String email = stamp() + "@teste.celke.uno";
        criarAdminService.criar("Admin do Comando", email, "senha-longa-do-admin");

        assertThat(entrar(email, "senha-longa-do-admin"))
                .bodyJson()
                .extractingPath("$.usuario.papeis")
                .asArray()
                .containsExactly("ADMIN");
    }

    @Test
    @DisplayName("hash BCrypt do sistema legado ($2y$) entra e é regravado em Argon2")
    void hashLegado() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        String hashLaravel =
                new BCryptPasswordEncoder().encode("senha-do-legado").replaceFirst("^\\$2a\\$", "\\$2y\\$");
        jdbc.sql("UPDATE usuarios SET senha_hash = :hash WHERE id = :id")
                .param("hash", hashLaravel)
                .param("id", usuario.id())
                .update();

        entrar(usuario.email(), "senha-do-legado");

        assertThat(senhaHash(usuario.id())).startsWith("{argon2}");
        entrar(usuario.email(), "senha-do-legado");
    }

    @Test
    @DisplayName("senha nova é gravada em Argon2, nunca em texto")
    void senhaEmArgon2() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);

        assertThat(senhaHash(usuario.id())).startsWith("{argon2}$argon2id$").doesNotContain(usuario.senha());
    }

    private MvcTestResult tentarLogin(String email, String senha) {
        return mvc.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha))
                .exchange();
    }

    private String senhaHash(Long id) {
        return jdbc.sql("SELECT senha_hash FROM usuarios WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .single();
    }
}
