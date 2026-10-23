package br.com.celke.cursos.shared.fila;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração da fila.
 *
 * @param maxTentativas tentativas antes de o job ir para ERRO
 * @param lease tempo que um job reservado fica com o worker; vencido, outro worker pode pegá-lo
 * @param backoffBase atraso depois da primeira falha (dobra a cada nova falha)
 * @param backoffMaximo atraso máximo entre tentativas
 * @param intervalo pausa do consumidor quando a fila está vazia
 * @param consumidorAtivo liga o consumo agendado (só no worker)
 */
@Validated
@ConfigurationProperties("app.fila")
public record FilaProperties(
        @Min(1) int maxTentativas,
        @NotNull Duration lease,
        @NotNull Duration backoffBase,
        @NotNull Duration backoffMaximo,
        @NotNull Duration intervalo,
        boolean consumidorAtivo) {}
