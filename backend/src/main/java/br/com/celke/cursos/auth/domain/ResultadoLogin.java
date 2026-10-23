package br.com.celke.cursos.auth.domain;

/**
 * Resultado registrado na auditoria. Nas falhas, o cliente só recebe "e-mail ou senha inválidos";
 * {@code EMAIL_NAO_CONFIRMADO} só acontece com a senha certa. Os administradores passam por
 * {@code SEGUNDO_FATOR_PENDENTE} (senha certa) antes do {@code SUCESSO}.
 */
public enum ResultadoLogin {
    SUCESSO,
    EMAIL_DESCONHECIDO,
    SENHA_INVALIDA,
    USUARIO_INATIVO,
    EMAIL_NAO_CONFIRMADO,
    BLOQUEADO,
    SEGUNDO_FATOR_PENDENTE,
    SEGUNDO_FATOR_INVALIDO,
    SEGUNDO_FATOR_CADASTRADO,
    CODIGO_RECUPERACAO
}
