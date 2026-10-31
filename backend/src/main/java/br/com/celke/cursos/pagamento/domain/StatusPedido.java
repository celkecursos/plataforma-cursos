package br.com.celke.cursos.pagamento.domain;

/** Status do pedido, igual para todos os meios de pagamento (cada gateway traduz os seus). */
public enum StatusPedido {
    /** Pedido aberto; o aluno ainda não pagou. */
    CRIADO,
    /** Boleto ou PIX gerado, pagamento em análise. */
    AGUARDANDO_PAGAMENTO,
    APROVADO,
    /** Fim da garantia: a venda não pode mais ser reembolsada pelo aluno. */
    CONCLUIDO,
    /** Pedido de reembolso ou disputa aberta. */
    EM_DISPUTA,
    /** Cobrança atrasada (em geral, renovação de assinatura). */
    ATRASADO,
    REEMBOLSADO,
    CHARGEBACK,
    CANCELADO,
    /** Boleto ou PIX vencido sem pagamento. */
    EXPIRADO
}
