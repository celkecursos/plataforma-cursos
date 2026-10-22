package br.com.celke.cursos.pagamento.domain;

/**
 * Meios de pagamento que a plataforma sabe usar. Um meio novo entra com um valor aqui e um bean de
 * {@link GatewayPagamento}; o núcleo não muda.
 */
public enum TipoGateway {
    HOTMART
}
