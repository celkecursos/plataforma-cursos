package br.com.celke.cursos.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.auth.application.AuthProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CookieRefreshTest {

    private static CookieRefresh cookie(boolean seguro) {
        return new CookieRefresh(new AuthProperties(
                "segredo", "plataforma-cursos", Duration.ofMinutes(10), Duration.ofDays(14), seguro));
    }

    @Test
    @DisplayName("cookie é HttpOnly, Secure, SameSite=Strict, só em /api/auth, sem Domain e vale 14 dias")
    void atributos() {
        assertThat(cookie(true).criar("abc").toString())
                .startsWith("refresh_token=abc;")
                .contains("Path=/api/auth", "Max-Age=1209600", "Secure", "HttpOnly", "SameSite=Strict")
                .doesNotContain("Domain");
    }

    @Test
    @DisplayName("limpar zera o cookie com os mesmos atributos")
    void limpar() {
        assertThat(cookie(true).limpar().toString())
                .startsWith("refresh_token=;")
                .contains("Max-Age=0", "Path=/api/auth", "HttpOnly", "SameSite=Strict");
    }

    @Test
    @DisplayName("Secure pode ser desligado por configuração")
    void semSecure() {
        assertThat(cookie(false).criar("abc").toString()).doesNotContain("Secure");
    }
}
