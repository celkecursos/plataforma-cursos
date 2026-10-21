package br.com.celke.cursos.matricula.domain;

/** Como a matrícula foi paga. {@code MANUAL} e {@code CORTESIA} são lançadas pelo ADM. */
public enum FormaPagamento {
    MANUAL,
    CORTESIA,
    PIX,
    CARTAO,
    BOLETO
}
