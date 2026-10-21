package br.com.celke.cursos.auth.application;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração da autenticação.
 *
 * @param jwtSegredo chave HMAC do JWT em Base64, com pelo menos 32 bytes
 * @param validadeAcesso validade do access token
 * @param validadeRefresh validade do refresh token (renovada a cada uso)
 * @param cookieSeguro marca o cookie de refresh como {@code Secure}
 */
@Validated
@ConfigurationProperties("app.auth")
public record AuthProperties(
        @NotBlank String jwtSegredo,
        @NotBlank String emissor,
        @NotNull Duration validadeAcesso,
        @NotNull Duration validadeRefresh,
        boolean cookieSeguro) {

    public byte[] chaveJwt() {
        return Base64.getDecoder().decode(jwtSegredo);
    }

    @AssertTrue(message = "app.auth.jwt-segredo deve estar em Base64 e ter pelo menos 32 bytes")
    public boolean isSegredoValido() {
        if (jwtSegredo == null || jwtSegredo.isBlank()) {
            return true; // @NotBlank já acusa
        }
        try {
            return chaveJwt().length >= 32;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
