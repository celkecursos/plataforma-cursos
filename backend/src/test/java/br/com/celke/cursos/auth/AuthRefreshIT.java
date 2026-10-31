package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.usuario.domain.Papel;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: renovação com rotação do refresh token, detecção de reutilização (revoga a família),
 * logout e "sair de todos os dispositivos".
 * Não cobre: regras de expiração caso a caso (RefreshTokenServiceTest).
 * Dado criado: usuários it-<stamp>@teste.celke.uno (cada teste usa o seu).
 */
class AuthRefreshIT extends IntegrationTest {

    @Test
    @DisplayName("refresh troca o cookie por um novo e emite outro access token")
    void rotacao() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        String cookie1 = cookieRefresh(entrar(usuario.email(), usuario.senha()));

        MvcTestResult renovado = refresh(cookie1);

        assertThat(renovado)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.usuario.email")
                .isEqualTo(usuario.email());
        String cookie2 = cookieRefresh(renovado);
        assertThat(cookie2).isNotEqualTo(cookie1);
        assertThat(mvc.get().uri("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken(renovado))))
                .hasStatusOk();
        assertThat(refresh(cookie2)).hasStatusOk();
    }

    @Test
    @DisplayName("refresh já usado que reaparece revoga a família inteira")
    void reutilizacaoRevogaFamilia() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        String cookie1 = cookieRefresh(entrar(usuario.email(), usuario.senha()));
        String cookie2 = cookieRefresh(refresh(cookie1));

        // Alguém reapresenta o token antigo (roubado): recusado e a família revogada
        assertSessaoInvalida(refresh(cookie1));
        // O token novo, que era legítimo, também deixa de valer
        assertSessaoInvalida(refresh(cookie2));
    }

    @Test
    @DisplayName("reutilização revoga só a família afetada, não as outras sessões do usuário")
    void reutilizacaoNaoAfetaOutraSessao() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        String sessaoA = cookieRefresh(entrar(usuario.email(), usuario.senha()));
        String sessaoB = cookieRefresh(entrar(usuario.email(), usuario.senha()));
        refresh(sessaoA);

        assertSessaoInvalida(refresh(sessaoA));

        assertThat(refresh(sessaoB)).hasStatusOk();
    }

    @Test
    @DisplayName("logout revoga a sessão e apaga o cookie")
    void logout() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        String cookie = cookieRefresh(entrar(usuario.email(), usuario.senha()));

        MvcTestResult saida = mvc.post()
                .uri("/api/auth/logout")
                .cookie(new Cookie("refresh_token", cookie))
                .exchange();

        assertThat(saida).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(saida.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("refresh_token=;")
                .contains("Max-Age=0", "Path=/api/auth");
        assertSessaoInvalida(refresh(cookie));
    }

    @Test
    @DisplayName("logout sem cookie responde 204 sem erro")
    void logoutSemCookie() {
        assertThat(mvc.post().uri("/api/auth/logout")).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("sair de todos os dispositivos revoga todas as sessões do usuário")
    void sairDeTodos() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        MvcTestResult sessaoA = entrar(usuario.email(), usuario.senha());
        String cookieB = cookieRefresh(entrar(usuario.email(), usuario.senha()));

        assertThat(mvc.post()
                        .uri("/api/auth/sair-de-todos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken(sessaoA))))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertSessaoInvalida(refresh(cookieRefresh(sessaoA)));
        assertSessaoInvalida(refresh(cookieB));
    }

    @Test
    @DisplayName("sair de todos não afeta as sessões de outro usuário")
    void sairDeTodosNaoAfetaOutroUsuario() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        UsuarioTeste outro = criarUsuario(Papel.ALUNO);
        String token = login(usuario.email(), usuario.senha());
        String cookieOutro = cookieRefresh(entrar(outro.email(), outro.senha()));

        mvc.post()
                .uri("/api/auth/sair-de-todos")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange();

        assertThat(refresh(cookieOutro)).hasStatusOk();
    }

    @Test
    @DisplayName("refresh sem cookie ou com cookie desconhecido é recusado")
    void cookieAusenteOuDesconhecido() {
        assertSessaoInvalida(mvc.post().uri("/api/auth/refresh").exchange());
        assertSessaoInvalida(refresh("token-que-nunca-existiu"));
    }

    private MvcTestResult refresh(String cookie) {
        return mvc.post()
                .uri("/api/auth/refresh")
                .cookie(new Cookie("refresh_token", cookie))
                .exchange();
    }

    private static String cookieRefresh(MvcTestResult resposta) {
        Cookie cookie = resposta.getResponse().getCookie("refresh_token");
        assertThat(cookie).as("cookie refresh_token na resposta").isNotNull();
        return cookie.getValue();
    }

    private static void assertSessaoInvalida(MvcTestResult resposta) {
        assertThat(resposta)
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Sessão expirada. Entre novamente.");
    }
}
