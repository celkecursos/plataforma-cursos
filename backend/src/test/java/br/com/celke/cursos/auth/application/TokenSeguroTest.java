package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenSeguroTest {

    @Test
    @DisplayName("hash é o SHA-256 em hexadecimal")
    void hashSha256() {
        assertThat(TokenSeguro.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("token tem 32 bytes em Base64 URL sem padding e cada um é diferente")
    void gerar() {
        String token = TokenSeguro.gerar();

        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(TokenSeguro.gerar()).isNotEqualTo(token);
    }
}
