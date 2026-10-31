package br.com.celke.cursos.pagamento.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Pagamentos.
 *
 * @param chave chave AES-256 dos segredos dos meios de pagamento, 32 bytes em Base64 (em branco: a API
 *     sobe, mas não grava segredos)
 * @param webhookTamanhoMaximo tamanho máximo do corpo de uma notificação
 * @param webhookRequisicoesPorIpPorMinuto notificações por IP a cada minuto
 * @param retencaoPayload depois desse prazo, o corpo dos eventos processados ou ignorados é apagado
 * @param disputaSuspende pedido em disputa tira o acesso até a disputa terminar
 * @param atrasoSuspendeApos pedido atrasado há mais que isso tira o acesso (nulo: nunca)
 */
@Validated
@ConfigurationProperties("app.pagamentos")
public record PagamentosProperties(
        String chave,
        @NotNull DataSize webhookTamanhoMaximo,
        @Min(1) int webhookRequisicoesPorIpPorMinuto,
        @NotNull Duration retencaoPayload,
        boolean disputaSuspende,
        Duration atrasoSuspendeApos,
        @NotNull Reconciliacao reconciliacao) {

    public PagamentosProperties {
        if (chave != null && !chave.isBlank()) {
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(chave.strip());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("PAGAMENTOS_CHAVE precisa estar em Base64.", e);
            }
            if (bytes.length != 32) {
                throw new IllegalArgumentException(
                        "PAGAMENTOS_CHAVE precisa ter 32 bytes (openssl rand -base64 32); tem " + bytes.length + ".");
            }
        }
    }

    /**
     * Reconciliação diária pela API dos meios de pagamento.
     *
     * @param ativa desligada por padrão (depende das credenciais da API)
     * @param pendenteApos pedido criado ou aguardando pagamento há mais que isso é consultado
     * @param semEventoApos pedido pago sem notificação há mais que isso é consultado
     * @param janela pedidos sem mudança há mais que isso deixam de ser consultados
     */
    public record Reconciliacao(
            boolean ativa,
            @NotNull Duration pendenteApos,
            @NotNull Duration semEventoApos,
            @NotNull Duration janela) {}
}
