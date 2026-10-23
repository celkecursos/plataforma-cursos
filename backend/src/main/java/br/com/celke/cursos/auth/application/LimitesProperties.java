package br.com.celke.cursos.auth.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Limites contra força bruta.
 *
 * @param requisicoesPorIpPorMinuto requisições a login e refresh por IP a cada minuto
 * @param falhasPorEmailEIp falhas de login seguidas que bloqueiam o par e-mail + IP
 * @param falhasPorEmail falhas de login seguidas, de qualquer IP, que bloqueiam o e-mail
 * @param janelaFalhas tempo sem novas falhas para o contador zerar
 * @param bloqueio duração do bloqueio
 */
@Validated
@ConfigurationProperties("app.limites")
public record LimitesProperties(
        @Min(1) int requisicoesPorIpPorMinuto,
        @Min(1) int falhasPorEmailEIp,
        @Min(1) int falhasPorEmail,
        @NotNull Duration janelaFalhas,
        @NotNull Duration bloqueio) {}
