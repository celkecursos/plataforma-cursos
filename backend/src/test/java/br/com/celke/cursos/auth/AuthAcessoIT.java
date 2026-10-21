package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Cobre: matriz de papéis das rotas de autenticação — públicas (login, refresh, logout) e privadas
 * (me, sair-de-todos) — para anônimo, ALUNO e ADMIN.
 * Não cobre: comportamento de cada rota (AuthLoginIT, AuthRefreshIT).
 * Dado criado: usuários it-<stamp>@teste.celke.uno para sair-de-todos (o seed é só leitura).
 */
class AuthAcessoIT extends IntegrationTest {

    static Stream<Arguments> rotasPublicas() {
        // Sem token não pode dar 401 da segurança: o resultado vem da própria rota
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/auth/login", HttpStatus.BAD_REQUEST),
                Arguments.of(HttpMethod.POST, "/api/auth/logout", HttpStatus.NO_CONTENT));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rotasPublicas")
    @DisplayName("rotas públicas respondem sem token")
    void rotasPublicasSemToken(HttpMethod metodo, String rota, HttpStatus esperado) {
        assertThat(mvc.method(metodo)
                        .uri(rota)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .hasStatus(esperado);
    }

    @Test
    @DisplayName("refresh sem token responde com a regra da sessão, não com o bloqueio da segurança")
    void refreshPublico() {
        assertThat(mvc.post().uri("/api/auth/refresh"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Sessão expirada. Entre novamente.");
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rotasPrivadas")
    @DisplayName("rotas privadas exigem token")
    void rotasPrivadasSemToken(HttpMethod metodo, String rota) {
        assertThat(mvc.method(metodo).uri(rota))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Autenticação necessária.");
    }

    static Stream<Arguments> rotasPrivadas() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/auth/me"), Arguments.of(HttpMethod.POST, "/api/auth/sair-de-todos"));
    }

    @Test
    @DisplayName("métodos não declarados nas rotas públicas exigem token")
    void metodoNaoDeclarado() {
        assertThat(mvc.get().uri("/api/auth/login")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.delete().uri("/api/auth/refresh")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("usuariosSeed")
    @DisplayName("/api/auth/me responde para ALUNO e ADMIN")
    void mePorPapel(String email) {
        assertThat(mvc.get()
                        .uri("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login(email, UsuariosSeed.SENHA))))
                .hasStatusOk();
    }

    static Stream<String> usuariosSeed() {
        return Stream.of(UsuariosSeed.ALUNO_EMAIL, UsuariosSeed.ADMIN_EMAIL);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("papeis")
    @DisplayName("sair-de-todos responde para ALUNO e ADMIN")
    void sairDeTodosPorPapel(Papel papel) {
        UsuarioTeste usuario = criarUsuario(papel);

        assertThat(mvc.post()
                        .uri("/api/auth/sair-de-todos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login(usuario.email(), usuario.senha()))))
                .hasStatus(HttpStatus.NO_CONTENT);
    }

    static Stream<Papel> papeis() {
        return Stream.of(Papel.ALUNO, Papel.ADMIN);
    }
}
