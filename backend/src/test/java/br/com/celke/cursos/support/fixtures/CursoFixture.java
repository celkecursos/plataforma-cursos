package br.com.celke.cursos.support.fixtures;

import br.com.celke.cursos.curso.domain.Aula;
import br.com.celke.cursos.curso.domain.Curso;
import br.com.celke.cursos.curso.domain.Modulo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.Turma;
import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;

/** Fábrica de cursos, turmas, módulos e aulas para testes unitários (sem banco: o id é preenchido aqui). */
public final class CursoFixture {

    public static final Instant CRIADO_EM = Instant.parse("2026-01-01T00:00:00Z");

    private CursoFixture() {}

    public static Curso curso(long id, String slug) {
        Curso curso = new Curso("Curso " + slug, slug, "Resumo de " + slug, null, CRIADO_EM);
        ReflectionTestUtils.setField(curso, "id", id);
        return curso;
    }

    public static Curso publicado(Curso curso) {
        curso.alterarStatus(StatusCurso.PUBLICADO, CRIADO_EM);
        return curso;
    }

    public static Curso comCapa(Curso curso, String chave) {
        curso.definirCapa(chave, CRIADO_EM);
        return curso;
    }

    public static Turma turma(long id, Curso curso) {
        Turma turma = new Turma(curso, "Turma " + id, CRIADO_EM);
        ReflectionTestUtils.setField(turma, "id", id);
        return turma;
    }

    public static Modulo modulo(long id, Turma turma, int ordem) {
        Modulo modulo = new Modulo(turma, "Módulo " + id, ordem, CRIADO_EM);
        ReflectionTestUtils.setField(modulo, "id", id);
        return modulo;
    }

    public static Aula aula(long id, Modulo modulo, int ordem) {
        Aula aula = new Aula(modulo, "Aula " + id, null, false, ordem, CRIADO_EM);
        ReflectionTestUtils.setField(aula, "id", id);
        return aula;
    }
}
