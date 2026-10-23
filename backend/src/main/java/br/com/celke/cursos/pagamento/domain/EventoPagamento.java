package br.com.celke.cursos.pagamento.domain;

import br.com.celke.cursos.matricula.domain.FormaPagamento;
import java.time.Instant;

/**
 * Notificação traduzida para o formato da plataforma, independente do meio de pagamento.
 *
 * @param idExterno id único do evento no meio de pagamento (a idempotência)
 * @param tipoOriginal nome do evento no meio de pagamento (ex.: {@code PURCHASE_APPROVED})
 * @param statusPedido status interno correspondente; nulo para evento que não muda o pedido
 * @param codigoPedido código do nosso pedido, quando o checkout o levou (Hotmart: {@code xcod})
 * @param valorCentavos valor pago em centavos; nulo quando o meio não informa
 * @param formaPagamento forma de pagamento; nula quando o meio não informa
 * @param assinatura dados da assinatura, quando a venda é recorrente
 */
public record EventoPagamento(
        String idExterno,
        String tipoOriginal,
        Instant ocorridoEm,
        String transacao,
        StatusPedido statusPedido,
        String codigoPedido,
        String produtoExterno,
        String ofertaExterna,
        CompradorEvento comprador,
        Long valorCentavos,
        String moeda,
        FormaPagamento formaPagamento,
        Assinatura assinatura) {

    /** Comprador como veio na notificação; o documento só mascarado. */
    public record CompradorEvento(String email, String nome, String documentoMascarado) {}

    public record Assinatura(String codigo, String status) {}
}
