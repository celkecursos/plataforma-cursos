package br.com.celke.cursos.pagamento.domain;

public enum OrigemPedido {
    /** Nasceu no botão "Comprar" da plataforma, com o código do pedido no checkout. */
    CHECKOUT,
    /** Venda que chegou sem o código do pedido (afiliado, link direto do meio de pagamento). */
    EXTERNO
}
