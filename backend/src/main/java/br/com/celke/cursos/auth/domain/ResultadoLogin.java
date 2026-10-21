package br.com.celke.cursos.auth.domain;

/**
 * Resultado registrado na auditoria. Nas falhas, o cliente só recebe "e-mail ou senha inválidos";
 * {@code EMAIL_NAO_CONFIRMADO} só acontece com a senha certa.
 */
public enum ResultadoLogin {
    SUCESSO,
    EMAIL_DESCONHECIDO,
    SENHA_INVALIDA,
    USUARIO_INATIVO,
    EMAIL_NAO_CONFIRMADO,
    BLOQUEADO
}
