package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.usuario.domain.Papel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: bloqueio do login depois de falhas seguidas (429 com Retry-After), liberação em outro IP,
 * auditoria de cada tentativa e o limite por IP ligado nas rotas de autenticação.
 * Não cobre: regras de tempo e contagem caso a caso (BloqueioLoginServiceTest, LimitePorIpFilterTest).
 * Dado criado: usuários it-<stamp>@teste.celke.uno e as tentativas deles na auditoria.
 */
class AuthBloqueioIT extends IntegrationTest {

    private static final String IP_ATACANTE = "203.0.113.50";
    private static final String IP_DONO = "198.51.100.20";

    @Autowired
    private JdbcClient jdbc;

    @Test
    @DisplayName("cinco senhas erradas bloqueiam o e-mail naquele IP, mesmo com a senha certa depois")
    void bloqueiaDepoisDeCincoFalhas() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        for (int i = 0; i < 5; i++) {
            assertThat(tentar(usuario.email(), "senha-errada", IP_ATACANTE)).hasStatus(HttpStatus.UNAUTHORIZED);
        }

        MvcTestResult bloqueado = tentar(usuario.email(), usuario.senha(), IP_ATACANTE);

        assertThat(bloqueado)
                .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .hasHeader(HttpHeaders.RETRY_AFTER, "900")
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Muitas tentativas. Tente novamente em 15 minutos.");
    }

    @Test
    @DisplayName("o dono da conta entra de outro IP enquanto o IP do atacante está bloqueado")
    void outroIpEntra() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        for (int i = 0; i < 5; i++) {
            tentar(usuario.email(), "senha-errada", IP_ATACANTE);
        }

        assertThat(tentar(usuario.email(), usuario.senha(), IP_DONO)).hasStatusOk();
    }

    @Test
    @DisplayName("toda tentativa fica na auditoria com resultado, IP e user-agent")
    void auditoria() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        String desconhecido = stamp() + "@teste.celke.uno";

        tentar(usuario.email(), "senha-errada", IP_ATACANTE);
        tentar(usuario.email(), usuario.senha(), IP_DONO);
        tentar(desconhecido, "qualquer", IP_ATACANTE);

        assertThat(auditoria(usuario.email()))
                .containsExactly(
                        new Tentativa("SENHA_INVALIDA", usuario.id(), IP_ATACANTE, "Teste/1.0"),
                        new Tentativa("SUCESSO", usuario.id(), IP_DONO, "Teste/1.0"));
        assertThat(auditoria(desconhecido))
                .containsExactly(new Tentativa("EMAIL_DESCONHECIDO", null, IP_ATACANTE, "Teste/1.0"));
    }

    @Test
    @DisplayName("tentativa barrada pelo bloqueio também fica na auditoria")
    void auditoriaDoBloqueio() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        for (int i = 0; i < 6; i++) {
            tentar(usuario.email(), "senha-errada", IP_ATACANTE);
        }

        assertThat(auditoria(usuario.email()))
                .extracting(Tentativa::resultado)
                .containsExactly(
                        "SENHA_INVALIDA",
                        "SENHA_INVALIDA",
                        "SENHA_INVALIDA",
                        "SENHA_INVALIDA",
                        "SENHA_INVALIDA",
                        "BLOQUEADO");
    }

    @Test
    @DisplayName("o limite por IP vale para as rotas de autenticação e conta, e não para o logout")
    void limitePorIpLigado() {
        assertThat(mvc.post()
                        .uri("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .headers()
                .containsHeader("X-RateLimit-Remaining");
        assertThat(mvc.post().uri("/api/auth/refresh")).headers().containsHeader("X-RateLimit-Remaining");
        assertThat(mvc.post()
                        .uri("/api/auth/recuperar-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .headers()
                .containsHeader("X-RateLimit-Remaining");
        assertThat(mvc.post().uri("/api/auth/logout")).headers().doesNotContainHeader("X-RateLimit-Remaining");
    }

    private MvcTestResult tentar(String email, String senha, String ip) {
        return mvc.post()
                .uri("/api/auth/login")
                .with(requisicao -> {
                    requisicao.setRemoteAddr(ip);
                    return requisicao;
                })
                .header(HttpHeaders.USER_AGENT, "Teste/1.0")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha))
                .exchange();
    }

    private List<Tentativa> auditoria(String email) {
        return jdbc.sql("""
                        SELECT resultado, usuario_id, ip, user_agent FROM auditoria_login
                         WHERE email = :email ORDER BY id
                        """)
                .param("email", email)
                .query((rs, n) -> new Tentativa(
                        rs.getString("resultado"),
                        (Long) rs.getObject("usuario_id"),
                        rs.getString("ip"),
                        rs.getString("user_agent")))
                .list();
    }

    private record Tentativa(String resultado, Long usuarioId, String ip, String userAgent) {}
}
