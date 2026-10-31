package br.com.celke.cursos.support.fixtures;

import br.com.celke.cursos.matricula.domain.FormaPagamento;
import br.com.celke.cursos.matricula.domain.Matricula;
import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;

/** Fábrica de matrículas para testes unitários (sem banco: o id é preenchido aqui). */
public final class MatriculaFixture {

    public static final Instant CRIADO_EM = Instant.parse("2026-01-01T00:00:00Z");

    private MatriculaFixture() {}

    /** Matrícula ativa do usuário na turma, no período informado ({@code fim} nulo: sem prazo). */
    public static Matricula matricula(long id, long usuarioId, long turmaId, Instant inicio, Instant fim) {
        Matricula matricula = new Matricula(usuarioId, turmaId, inicio, fim, FormaPagamento.MANUAL, null, CRIADO_EM);
        ReflectionTestUtils.setField(matricula, "id", id);
        return matricula;
    }

    public static Matricula cancelada(Matricula matricula) {
        matricula.cancelar(CRIADO_EM);
        return matricula;
    }
}
