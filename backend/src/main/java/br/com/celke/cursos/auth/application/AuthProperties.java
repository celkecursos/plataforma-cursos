package br.com.celke.cursos.auth.application;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração da autenticação.
 *
 * @param jwtSegredo chave HMAC do JWT em Base64, com pelo menos 32 bytes
 * @param validadeAcesso validade do access token
 * @param validadeRefresh validade do refresh token (renovada a cada uso)
 * @param cookieSeguro marca o cookie de refresh como {@code Secure}
 * @param origensPermitidas origens do front aceitas no refresh e no logout (ex.: {@code https://aluno.celke.uno})
 */
@Validated
@ConfigurationProperties("app.auth")
public record AuthProperties(
        @NotBlank String jwtSegredo,
        @NotBlank String emissor,
        @NotNull Duration validadeAcesso,
        @NotNull Duration validadeRefresh,
        boolean cookieSeguro,
        List<String> origensPermitidas) {

    public AuthProperties {
        origensPermitidas = origensPermitidas == null
                ? List.of()
                : origensPermitidas.stream()
                        .map(AuthProperties::normalizarOrigem)
                        .filter(o -> !o.isEmpty())
                        .toList();
    }

    /** Origem em minúsculas e sem barra no fim, para comparar com o cabeçalho {@code Origin}. */
    public static String normalizarOrigem(String origem) {
        String normalizada = origem.strip().toLowerCase(Locale.ROOT);
        return normalizada.endsWith("/") ? normalizada.substring(0, normalizada.length() - 1) : normalizada;
    }

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
