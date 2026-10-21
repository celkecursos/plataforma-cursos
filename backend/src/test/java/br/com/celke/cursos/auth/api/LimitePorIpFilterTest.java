package br.com.celke.cursos.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.auth.application.LimitesProperties;
import br.com.celke.cursos.support.fixtures.RelogioAjustavel;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** Filtro testado direto, com limite pequeno: 3 requisições por minuto. */
class LimitePorIpFilterTest {

    private RelogioAjustavel relogio;
    private LimitePorIpFilter filtro;

    @BeforeEach
    void criar() {
        relogio = new RelogioAjustavel(Instant.parse("2026-01-10T12:00:00Z"));
        filtro = new LimitePorIpFilter(
                new LimitesProperties(3, 5, 20, Duration.ofHours(1), Duration.ofMinutes(15)),
                JsonMapper.builder().build(),
                relogio);
    }

    private MockHttpServletResponse enviar(String metodo, String rota, String ip) throws Exception {
        var request = new MockHttpServletRequest(metodo, rota);
        request.setRemoteAddr(ip);
        var response = new MockHttpServletResponse();
        filtro.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    @DisplayName("três requisições passam; a quarta recebe 429 com Retry-After e ProblemDetail")
    void excedeLimite() throws Exception {
        for (int restante = 2; restante >= 0; restante--) {
            MockHttpServletResponse ok = enviar("POST", "/api/auth/login", "203.0.113.7");
            assertThat(ok.getStatus()).isEqualTo(200);
            assertThat(ok.getHeader("X-RateLimit-Remaining")).isEqualTo(String.valueOf(restante));
        }

        MockHttpServletResponse barrada = enviar("POST", "/api/auth/login", "203.0.113.7");

        assertThat(barrada.getStatus()).isEqualTo(429);
        assertThat(barrada.getContentType()).isEqualTo("application/problem+json");
        assertThat(barrada.getHeader("Retry-After")).isEqualTo("20");
        assertThat(barrada.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(barrada.getContentAsString())
                .contains("\"status\":429", "\"detail\":\"Muitas tentativas. Tente novamente em 1 minuto.\"");
    }

    @Test
    @DisplayName("o balde se recompõe com o tempo: 20 segundos devolvem uma requisição")
    void recompoe() throws Exception {
        for (int i = 0; i < 3; i++) {
            enviar("POST", "/api/auth/login", "203.0.113.7");
        }

        relogio.avancar(Duration.ofSeconds(19));
        assertThat(enviar("POST", "/api/auth/login", "203.0.113.7").getStatus()).isEqualTo(429);

        relogio.avancar(Duration.ofSeconds(1));
        assertThat(enviar("POST", "/api/auth/login", "203.0.113.7").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("cada IP tem o próprio limite")
    void porIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            enviar("POST", "/api/auth/login", "203.0.113.7");
        }

        assertThat(enviar("POST", "/api/auth/login", "198.51.100.9").getStatus())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("login e refresh dividem o mesmo limite")
    void loginERefreshJuntos() throws Exception {
        enviar("POST", "/api/auth/login", "203.0.113.7");
        enviar("POST", "/api/auth/refresh", "203.0.113.7");
        enviar("POST", "/api/auth/login", "203.0.113.7");

        assertThat(enviar("POST", "/api/auth/refresh", "203.0.113.7").getStatus())
                .isEqualTo(429);
    }

    @Test
    @DisplayName("outras rotas e outros métodos não são limitados nem contam")
    void foraDoLimite() throws Exception {
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse me = enviar("GET", "/api/auth/me", "203.0.113.7");
            assertThat(me.getStatus()).isEqualTo(200);
            assertThat(me.getHeader("X-RateLimit-Remaining")).isNull();
            enviar("GET", "/api/auth/login", "203.0.113.7");
        }

        assertThat(enviar("POST", "/api/auth/login", "203.0.113.7").getHeader("X-RateLimit-Remaining"))
                .isEqualTo("2");
    }
}
