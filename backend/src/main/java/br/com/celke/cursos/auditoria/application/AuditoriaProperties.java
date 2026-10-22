package br.com.celke.cursos.auditoria.application;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Trilha de auditoria.
 *
 * @param retencao registros mais antigos que isso são apagados pela limpeza semanal do worker
 */
@Validated
@ConfigurationProperties("app.auditoria")
public record AuditoriaProperties(@NotNull Duration retencao) {}
