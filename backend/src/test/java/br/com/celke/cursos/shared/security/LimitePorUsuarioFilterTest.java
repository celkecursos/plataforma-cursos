package br.com.celke.cursos.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.fixtures.RelogioAjustavel;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;

/** Filtro testado direto, com limites pequenos: 3 gravações por minuto e 2 checkouts por hora. */
class LimitePorUsuarioFilterTest {

    private RelogioAjustavel relogio;
    private LimitePorUsuarioFilter filtro;

    @BeforeEach
    void criar() {
        relogio = new RelogioAjustavel(Instant.parse("2026-01-10T12:00:00Z"));
        filtro = new LimitePorUsuarioFilter(
                new LimitesUsuarioProperties(3, 2), JsonMapper.builder().build(), relogio);
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private int enviar(String metodo, String rota, String usuario) throws Exception {
        SecurityContextHolder.clearContext();
        if (usuario != null) {
            Jwt jwt = Jwt.withTokenValue("t")
                    .header("alg", "HS256")
                    .subject(usuario)
                    .build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(new MockHttpServletRequest(metodo, rota), response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    @DisplayName("passou do limite de gravações: 429 com Retry-After; o minuto seguinte libera")
    void limiteDeEscritas() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(enviar("POST", "/api/forum/topicos", "7")).isEqualTo(200);
        }
        assertThat(enviar("PUT", "/api/perfil", "7")).isEqualTo(429);

        relogio.avancar(Duration.ofMinutes(1));
        assertThat(enviar("DELETE", "/api/perfil/foto", "7")).isEqualTo(200);
    }

    @Test
    @DisplayName("cada usuário tem o próprio limite; leitura e anônimo não contam")
    void porUsuario() throws Exception {
        for (int i = 0; i < 3; i++) {
            enviar("POST", "/api/forum/topicos", "7");
        }
        assertThat(enviar("POST", "/api/forum/topicos", "8")).isEqualTo(200);
        assertThat(enviar("GET", "/api/perfil", "7")).isEqualTo(200);
        assertThat(enviar("POST", "/api/auth/login", null)).isEqualTo(200);
    }

    @Test
    @DisplayName("o checkout tem limite próprio por hora")
    void limiteDeCheckout() throws Exception {
        assertThat(enviar("POST", "/api/pagamentos/checkout", "7")).isEqualTo(200);
        assertThat(enviar("POST", "/api/pagamentos/checkout", "7")).isEqualTo(200);
        relogio.avancar(Duration.ofMinutes(2));
        assertThat(enviar("POST", "/api/pagamentos/checkout", "7")).isEqualTo(429);

        relogio.avancar(Duration.ofHours(1));
        assertThat(enviar("POST", "/api/pagamentos/checkout", "7")).isEqualTo(200);
    }
}
