package br.com.celke.cursos.auth.domain;

public enum TipoToken {
    CONFIRMACAO_EMAIL,
    RECUPERACAO_SENHA,
    /** Segunda etapa do login do administrador: não vai por e-mail. */
    DESAFIO_SEGUNDO_FATOR;

    /** Tokens enviados por e-mail contam no limite de e-mails por hora. */
    public boolean porEmail() {
        return this != DESAFIO_SEGUNDO_FATOR;
    }
}
