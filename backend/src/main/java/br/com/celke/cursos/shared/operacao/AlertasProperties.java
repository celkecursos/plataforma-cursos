package br.com.celke.cursos.shared.operacao;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Alertas de operação enviados pelo worker.
 *
 * @param email destinatário; em branco, os alertas ficam desligados
 * @param discoMinimoPercentual espaço livre abaixo do qual o disco gera alerta
 * @param diretorioDisco caminho cujo sistema de arquivos é medido (no container, o disco da VPS)
 * @param repeticaoDisco intervalo mínimo entre dois alertas de disco enquanto o espaço continua baixo
 */
@Validated
@ConfigurationProperties("app.alertas")
public record AlertasProperties(
        String email,
        @Min(1) @Max(99) int discoMinimoPercentual,
        @NotNull Path diretorioDisco,
        @NotNull Duration repeticaoDisco) {

    public boolean ligados() {
        return email != null && !email.isBlank();
    }
}
