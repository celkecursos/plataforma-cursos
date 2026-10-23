package br.com.celke.cursos.auth.application;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** @param retencao tempo de guarda das tentativas de login (e-mail, IP e navegador) */
@Validated
@ConfigurationProperties("app.auth.auditoria-login")
public record AuditoriaLoginProperties(@NotNull Duration retencao) {}
