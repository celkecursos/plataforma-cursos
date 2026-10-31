package br.com.celke.cursos.shared.security;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Limites por usuário logado nas rotas que gravam (POST, PUT, PATCH, DELETE): uma conta roubada ou um
 * script não enchem o banco, a fila nem o meio de pagamento.
 *
 * @param escritasPorMinuto gravações de um usuário a cada minuto, somando todas as rotas
 * @param checkoutsPorHora pedidos abertos por um usuário a cada hora
 */
@Validated
@ConfigurationProperties("app.limites-usuario")
public record LimitesUsuarioProperties(
        @Min(1) int escritasPorMinuto, @Min(1) int checkoutsPorHora) {}
