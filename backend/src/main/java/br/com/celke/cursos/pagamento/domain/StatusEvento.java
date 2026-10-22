package br.com.celke.cursos.pagamento.domain;

/** Situação de uma notificação recebida. */
public enum StatusEvento {
    /** Gravada e na fila para processar. */
    RECEBIDO,
    PROCESSADO,
    /** Válida, mas não se aplica (oferta não cadastrada, evento não tratado, evento antigo). */
    IGNORADO,
    /** Não pôde ser interpretada ou o processamento desistiu. */
    ERRO
}
