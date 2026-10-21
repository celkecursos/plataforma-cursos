package br.com.celke.cursos.auth.domain;

/** Resultado registrado na auditoria. O cliente só recebe "e-mail ou senha inválidos". */
public enum ResultadoLogin {
    SUCESSO,
    EMAIL_DESCONHECIDO,
    SENHA_INVALIDA,
    USUARIO_INATIVO,
    BLOQUEADO
}
