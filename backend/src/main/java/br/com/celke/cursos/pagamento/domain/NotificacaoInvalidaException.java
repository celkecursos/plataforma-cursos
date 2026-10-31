package br.com.celke.cursos.pagamento.domain;

/** Notificação autenticada que não pôde ser interpretada (JSON inválido, sem id, sem evento). */
public class NotificacaoInvalidaException extends RuntimeException {

    public NotificacaoInvalidaException(String mensagem) {
        super(mensagem);
    }

    public NotificacaoInvalidaException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
