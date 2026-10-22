package br.com.celke.cursos.video.domain;

/** Situação do último envio de vídeo da aula. */
public enum StatusVideo {
    /** URLs de envio geradas; o navegador ainda está enviando as partes. */
    AGUARDANDO_ENVIO,
    /** Arquivo conferido e job na fila. */
    PENDENTE,
    PROCESSANDO,
    PRONTO,
    ERRO;

    /** Na fila ou com o worker: não aceita novo envio, reprocessamento nem exclusão. */
    public boolean emProcessamento() {
        return this == PENDENTE || this == PROCESSANDO;
    }
}
