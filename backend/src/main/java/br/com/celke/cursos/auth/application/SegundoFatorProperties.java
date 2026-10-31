package br.com.celke.cursos.auth.application;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Segundo fator (TOTP) dos administradores.
 *
 * @param chave chave AES-256 dos segredos TOTP guardados no banco, 32 bytes em Base64 ({@code TOTP_CHAVE})
 * @param emissor nome que o aplicativo autenticador mostra ao lado da conta
 * @param rejeitarReuso recusa um código já usado (desligado só nos testes, que entram várias vezes no
 *     mesmo intervalo de 30 segundos)
 */
@Validated
@ConfigurationProperties("app.auth.segundo-fator")
public record SegundoFatorProperties(
        @NotBlank String chave, @NotBlank String emissor, boolean rejeitarReuso) {

    @AssertTrue(message = "app.auth.segundo-fator.chave deve ter 32 bytes em Base64 (openssl rand -base64 32)")
    public boolean isChaveValida() {
        if (chave == null || chave.isBlank()) {
            return true; // @NotBlank já acusa
        }
        try {
            return Base64.getDecoder().decode(chave).length == 32;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
