package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.usuario.domain.Papel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Usuários no ADM: só o admin acessa; bloquear derruba o token na hora e impede o login; encerrar as
 * sessões vale para os tokens já emitidos; o admin não bloqueia a si mesmo; tudo fica na trilha.
 */
class UsuariosAdmIT extends IntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    private UsuarioTeste admin;
    private String tokenAdmin;

    @BeforeEach
    void entrarComoAdmin() {
        admin = criarUsuario(Papel.ADMIN);
        tokenAdmin = login(admin.email(), admin.senha());
    }

    private MvcTestResult situacao(Long id, boolean ativo) {
        return mvc.patch()
                .uri("/api/adm/usuarios/{id}/situacao", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ativo\":%s}".formatted(ativo))
                .exchange();
    }

    private MvcTestResult perfil(String token) {
        return mvc.get()
                .uri("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange();
    }

    @Test
    @DisplayName("anônimo 401, aluno 403, admin lista e busca por e-mail")
    void acesso() {
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);

        assertThat(mvc.get().uri("/api/adm/usuarios")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get()
                        .uri("/api/adm/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login(aluno.email(), aluno.senha()))))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get()
                        .uri(
                                "/api/adm/usuarios?busca={b}&papel=ALUNO",
                                aluno.email().toUpperCase())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.itens[0].email")
                .isEqualTo(aluno.email());
    }

    @Test
    @DisplayName("bloquear derruba o token na hora e impede o login; desbloquear devolve o acesso")
    void bloquear() {
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);
        String token = login(aluno.email(), aluno.senha());
        assertThat(perfil(token)).hasStatusOk();

        assertThat(situacao(aluno.id(), false))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.ativo")
                .isEqualTo(false);

        assertThat(perfil(token)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.post()
                        .uri("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(aluno.email(), aluno.senha())))
                .hasStatus(HttpStatus.UNAUTHORIZED);

        assertThat(situacao(aluno.id(), true)).hasStatusOk();
        assertThat(perfil(login(aluno.email(), aluno.senha()))).hasStatusOk();

        assertThat(jdbc.sql("""
                        SELECT count(*) FROM auditoria
                         WHERE entidade = 'Usuario' AND entidade_id = ? AND operacao = 'ALTERACAO'
                           AND usuario_id = ? AND depois ->> 'ativo' = 'false'
                        """)
                        .param(String.valueOf(aluno.id()))
                        .param(admin.id())
                        .query(Long.class)
                        .single())
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("encerrar as sessões invalida os tokens já emitidos; um login novo funciona")
    void encerrarSessoes() throws Exception {
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);
        String antigo = login(aluno.email(), aluno.senha());
        Thread.sleep(1_100); // o iat do JWT tem precisão de segundos

        assertThat(mvc.post()
                        .uri("/api/adm/usuarios/{id}/encerrar-sessoes", aluno.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin)))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(perfil(antigo)).hasStatus(HttpStatus.UNAUTHORIZED);
        Thread.sleep(1_100);
        assertThat(perfil(login(aluno.email(), aluno.senha()))).hasStatusOk();
    }

    @Test
    @DisplayName("o admin não bloqueia a própria conta")
    void naoBloqueiaASiMesmo() {
        assertThat(situacao(admin.id(), false)).hasStatus(HttpStatus.CONFLICT);
        assertThat(perfil(tokenAdmin)).hasStatusOk();
    }
}
