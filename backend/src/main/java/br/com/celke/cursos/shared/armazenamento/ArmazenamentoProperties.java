package br.com.celke.cursos.shared.armazenamento;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração do S3 (Garage). A aplicação não sobe se faltar algum valor obrigatório.
 *
 * @param endpoint endereço usado pela aplicação (rede interna, ex.: {@code http://garage:3900})
 * @param endpointPublico endereço que vai nas URLs pré-assinadas (o que o navegador acessa)
 * @param corsOrigens origens do front que podem enviar e baixar direto do bucket
 * @param diasUploadsAbandonados dias até o bucket apagar o que ficou em {@code uploads/}
 */
@Validated
@ConfigurationProperties("app.armazenamento")
public record ArmazenamentoProperties(
        @NotBlank String endpoint,
        @NotBlank String endpointPublico,
        @NotBlank String regiao,
        @NotBlank String bucket,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        List<String> corsOrigens,
        @Min(1) int diasUploadsAbandonados) {

    public ArmazenamentoProperties {
        corsOrigens = corsOrigens == null
                ? List.of()
                : corsOrigens.stream()
                        .map(String::strip)
                        .filter(o -> !o.isEmpty())
                        .toList();
    }
}
