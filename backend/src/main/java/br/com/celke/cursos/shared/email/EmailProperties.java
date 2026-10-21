package br.com.celke.cursos.shared.email;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Remetente dos e-mails. O servidor SMTP é configurado em {@code spring.mail}. */
@Validated
@ConfigurationProperties("app.email")
public record EmailProperties(
        @NotBlank @Email String remetente, @NotBlank String nomeRemetente) {}
