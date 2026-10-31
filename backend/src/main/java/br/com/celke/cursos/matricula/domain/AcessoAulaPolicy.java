package br.com.celke.cursos.matricula.domain;

import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * Política única de acesso ao conteúdo do curso (aula, vídeo, arquivo, fórum, progresso,
 * certificado): matrícula vigente na turma da aula, ou aula aberta (gratuita, de turma ativa e curso
 * publicado). Ninguém decide acesso ao conteúdo fora daqui.
 */
@Component
public class AcessoAulaPolicy {

    private final Clock clock;

    public AcessoAulaPolicy(Clock clock) {
        this.clock = clock;
    }

    /**
     * @param matricula matrícula do aluno na turma da aula, ou {@code null} se ele não tem
     * @param aulaAberta aula gratuita de turma ativa e curso publicado
     */
    public boolean podeAcessarAula(Matricula matricula, boolean aulaAberta) {
        return aulaAberta || podeAcessarTurma(matricula);
    }

    /** Conteúdo completo da turma (lista de módulos e aulas, progresso, certificado): só com matrícula vigente. */
    public boolean podeAcessarTurma(Matricula matricula) {
        return matricula != null && matricula.isVigente(clock.instant());
    }
}
