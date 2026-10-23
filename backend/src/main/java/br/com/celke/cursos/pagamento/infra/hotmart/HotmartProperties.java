package br.com.celke.cursos.pagamento.infra.hotmart;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Endereços da API da Hotmart (confira na documentação antes de mudar).
 *
 * @param urlToken token OAuth por client credentials
 * @param urlApi API de pagamentos em produção (histórico de vendas)
 * @param urlApiSandbox API de pagamentos no sandbox
 * @param timeout tempo máximo de cada chamada
 */
@Validated
@ConfigurationProperties("app.pagamentos.hotmart")
public record HotmartProperties(
        @NotBlank String urlToken,
        @NotBlank String urlApi,
        @NotBlank String urlApiSandbox,
        @NotNull Duration timeout) {}
