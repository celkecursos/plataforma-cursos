package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.usuario.domain.Papel;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: proteção contra CSRF no refresh e no logout — origem fora da lista e Sec-Fetch-Site
 * cross-site recusados sem tocar na sessão; origem permitida aceita.
 * Não cobre: regras de comparação caso a caso (VerificadorOrigemTest).
 * Dado criado: usuários it-<stamp>@teste.celke.uno.
 */
class AuthOrigemIT extends IntegrationTest {

    private static final String ORIGEM_PERMITIDA = "http://localhost:5173";
    private static final String OUTRA_APP_DO_DOMINIO = "https://outra-app.celke.uno";

    @Test
    @DisplayName("refresh vindo de outra origem é recusado e a sessão continua valendo")
    void refreshDeOutraOrigem() {
        String cookie = cookieDeLogin();

        assertOrigemRecusada(refresh(cookie, OUTRA_APP_DO_DOMINIO, null));

        assertThat(refresh(cookie, ORIGEM_PERMITIDA, "same-origin")).hasStatusOk();
    }

    @Test
    @DisplayName("refresh marcado como cross-site é recusado")
    void refreshCrossSite() {
        assertOrigemRecusada(refresh(cookieDeLogin(), null, "cross-site"));
    }

    @Test
    @DisplayName("logout vindo de outra origem é recusado e não encerra a sessão")
    void logoutDeOutraOrigem() {
        String cookie = cookieDeLogin();

        assertOrigemRecusada(mvc.post()
                .uri("/api/auth/logout")
                .header("Origin", OUTRA_APP_DO_DOMINIO)
                .cookie(new Cookie("refresh_token", cookie))
                .exchange());

        assertThat(refresh(cookie, null, null)).hasStatusOk();
    }

    @Test
    @DisplayName("logout da origem permitida encerra a sessão")
    void logoutDaOrigemPermitida() {
        String cookie = cookieDeLogin();

        assertThat(mvc.post()
                        .uri("/api/auth/logout")
                        .header("Origin", ORIGEM_PERMITIDA)
                        .cookie(new Cookie("refresh_token", cookie)))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(refresh(cookie, null, null)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private String cookieDeLogin() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        return entrar(usuario.email(), usuario.senha())
                .getResponse()
                .getCookie("refresh_token")
                .getValue();
    }

    private MvcTestResult refresh(String cookie, String origem, String secFetchSite) {
        var requisicao = mvc.post().uri("/api/auth/refresh").cookie(new Cookie("refresh_token", cookie));
        if (origem != null) {
            requisicao.header("Origin", origem);
        }
        if (secFetchSite != null) {
            requisicao.header("Sec-Fetch-Site", secFetchSite);
        }
        return requisicao.exchange();
    }

    private static void assertOrigemRecusada(MvcTestResult resposta) {
        assertThat(resposta)
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Origem não permitida.");
    }
}
