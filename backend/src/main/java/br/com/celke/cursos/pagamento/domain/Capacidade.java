package br.com.celke.cursos.pagamento.domain;

/** O que um meio de pagamento sabe fazer. */
public enum Capacidade {
    /** Checkout numa página do meio de pagamento (o aluno é redirecionado). */
    CHECKOUT_REDIRECIONAMENTO,
    /** Checkout num formulário dentro da plataforma. */
    CHECKOUT_EMBUTIDO,
    /** Consulta da situação de uma transação pela API (reconciliação). */
    CONSULTA_API,
    /** Reembolso pedido pela API. */
    REEMBOLSO_API,
    /** Assinaturas com cobrança recorrente. */
    RECORRENCIA
}
