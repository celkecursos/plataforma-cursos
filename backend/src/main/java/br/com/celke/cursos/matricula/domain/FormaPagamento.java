package br.com.celke.cursos.matricula.domain;

/**
 * Como a matrícula foi paga. {@code MANUAL} e {@code CORTESIA} são lançadas pelo ADM; {@code OUTRO}
 * cobre os meios sem valor próprio (PayPal, Google Pay...).
 */
public enum FormaPagamento {
    MANUAL,
    CORTESIA,
    PIX,
    CARTAO,
    BOLETO,
    OUTRO
}
