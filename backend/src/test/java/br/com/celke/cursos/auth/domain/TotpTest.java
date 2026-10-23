package br.com.celke.cursos.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TotpTest {

    /** Segredo dos vetores de teste da RFC 6238 (SHA-1): "12345678901234567890". */
    private static final String SEGREDO_RFC = Totp.base32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @ParameterizedTest(name = "{0} s → {1}")
    @CsvSource({"59, 287082", "1111111109, 081804", "1111111111, 050471", "1234567890, 005924", "2000000000, 279037"})
    @DisplayName("gera os códigos dos vetores da RFC 6238 (6 dígitos)")
    void vetoresRfc(long segundos, String esperado) {
        assertThat(Totp.codigo(SEGREDO_RFC, Totp.passo(Instant.ofEpochSecond(segundos))))
                .isEqualTo(esperado);
    }

    @Test
    @DisplayName("aceita um passo antes e um depois; fora disso, recusa")
    void tolerancia() {
        Instant agora = Instant.ofEpochSecond(1_111_111_111);
        long passo = Totp.passo(agora);

        assertThat(Totp.verificar(SEGREDO_RFC, Totp.codigo(SEGREDO_RFC, passo - 1), agora, null))
                .hasValue(passo - 1);
        assertThat(Totp.verificar(SEGREDO_RFC, Totp.codigo(SEGREDO_RFC, passo + 1), agora, null))
                .hasValue(passo + 1);
        assertThat(Totp.verificar(SEGREDO_RFC, Totp.codigo(SEGREDO_RFC, passo - 2), agora, null))
                .isEmpty();
    }

    @Test
    @DisplayName("o mesmo código não serve duas vezes (passo já usado)")
    void semReuso() {
        Instant agora = Instant.ofEpochSecond(1_111_111_111);
        long passo = Totp.passo(agora);
        String codigo = Totp.codigo(SEGREDO_RFC, passo);

        assertThat(Totp.verificar(SEGREDO_RFC, codigo, agora, passo - 1)).hasValue(passo);
        assertThat(Totp.verificar(SEGREDO_RFC, codigo, agora, passo)).isEmpty();
    }

    @ParameterizedTest(name = "[{0}]")
    @CsvSource({"''", "12345", "1234567", "abcdef"})
    @DisplayName("código fora do formato é recusado")
    void formato(String codigo) {
        assertThat(Totp.verificar(SEGREDO_RFC, codigo, Instant.now(), null)).isEmpty();
    }

    @Test
    @DisplayName("Base32 vai e volta; o segredo novo tem 160 bits")
    void base32() {
        byte[] bytes = "qualquer coisa".getBytes(StandardCharsets.UTF_8);
        assertThat(Totp.decodificarBase32(Totp.base32(bytes))).isEqualTo(bytes);
        assertThat(Totp.decodificarBase32(Totp.gerarSegredo())).hasSize(20);
    }

    @Test
    @DisplayName("o endereço do QR code leva emissor, conta e parâmetros")
    void uri() {
        assertThat(Totp.uri("Plataforma de Cursos", "ana@celke.uno", "ABC"))
                .isEqualTo("otpauth://totp/Plataforma%20de%20Cursos:ana%40celke.uno?secret=ABC"
                        + "&issuer=Plataforma%20de%20Cursos&algorithm=SHA1&digits=6&period=30");
    }
}
