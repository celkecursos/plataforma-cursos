package br.com.celke.cursos.auth.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cadastro, confirmação de e-mail e recuperação de senha.
 *
 * @param urlAluno endereço da área do aluno, base dos links enviados por e-mail
 * @param validadeConfirmacao validade do link de confirmação de e-mail
 * @param validadeRecuperacao validade do link de recuperação de senha
 * @param emailsPorHora e-mails do mesmo tipo por conta a cada hora (contra uso da plataforma para lotar a caixa de alguém)
 */
@Validated
@ConfigurationProperties("app.conta")
public record ContaProperties(
        @NotBlank String urlAluno,
        @NotNull Duration validadeConfirmacao,
        @NotNull Duration validadeRecuperacao,
        @Min(1) int emailsPorHora) {

    public ContaProperties {
        if (urlAluno != null && urlAluno.endsWith("/")) {
            urlAluno = urlAluno.substring(0, urlAluno.length() - 1);
        }
    }
}
