package br.com.celke.cursos.usuario.application;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Hash de senha: novas senhas em Argon2 ({@code {argon2}...}). Hashes BCrypt sem prefixo, como os
 * {@code $2y$} do Laravel, ainda são aceitos e regravados em Argon2 no próximo login.
 */
@Configuration
public class SenhaConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder(
                "argon2",
                Map.of(
                        "argon2",
                        Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
                        "bcrypt",
                        new BCryptPasswordEncoder()));
        encoder.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());
        return encoder;
    }
}
