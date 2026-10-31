package br.com.celke.cursos.matricula.domain;

/** Situação num instante, calculada pelo status e pelas datas. Só {@code VIGENTE} dá acesso ao conteúdo. */
public enum SituacaoMatricula {
    AGUARDANDO_INICIO,
    VIGENTE,
    VENCIDA,
    CANCELADA
}
