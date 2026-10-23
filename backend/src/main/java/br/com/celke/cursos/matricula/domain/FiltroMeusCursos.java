package br.com.celke.cursos.matricula.domain;

import java.time.Instant;

/**
 * Abas de "meus cursos". Ativos e arquivados incluem a matrícula que ainda vai começar; expirados são
 * as vencidas. Cancelada não aparece em nenhuma.
 */
public enum FiltroMeusCursos {
    ATIVOS,
    ARQUIVADOS,
    EXPIRADOS;

    public boolean aceita(Matricula matricula, Instant agora) {
        SituacaoMatricula situacao = matricula.situacao(agora);
        boolean emAndamento = situacao == SituacaoMatricula.VIGENTE || situacao == SituacaoMatricula.AGUARDANDO_INICIO;
        return switch (this) {
            case ATIVOS -> emAndamento && !matricula.isArquivada();
            case ARQUIVADOS -> emAndamento && matricula.isArquivada();
            case EXPIRADOS -> situacao == SituacaoMatricula.VENCIDA;
        };
    }
}
