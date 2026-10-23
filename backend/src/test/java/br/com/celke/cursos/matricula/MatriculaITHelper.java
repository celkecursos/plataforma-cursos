package br.com.celke.cursos.matricula;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Monta curso publicado com conteúdo e matrículas pela API do ADM, para os testes dos módulos que dependem do acesso ao conteúdo. */
public final class MatriculaITHelper {

    private final CursoITHelper adm;

    public MatriculaITHelper(CursoITHelper adm) {
        this.adm = adm;
    }

    public CursoITHelper adm() {
        return adm;
    }

    /** Curso publicado com turma ativa e um módulo com uma aula paga e uma gratuita. */
    public Estrutura cursoPublicado(String slug) {
        long curso = adm.criarCurso("IT Curso " + slug, slug);
        long turma = adm.criarTurma(curso, "Turma");
        adm.ativarTurma(turma);
        long modulo = adm.criarModulo(turma, "Módulo");
        long paga = adm.criarAula(modulo, "Aula paga", false);
        long gratuita = adm.criarAula(modulo, "Aula gratuita", true);
        adm.publicar(curso);
        return new Estrutura(curso, turma, modulo, paga, gratuita);
    }

    public MvcTestResult matricular(long turma, String email, Instant inicio, Instant fim) {
        return adm.enviar(
                "POST",
                "/api/adm/turmas/" + turma + "/matriculas",
                "{\"email\":\"%s\",\"formaPagamento\":\"MANUAL\"%s%s}"
                        .formatted(
                                email,
                                inicio == null ? "" : ",\"inicio\":\"" + inicio + "\"",
                                fim == null ? "" : ",\"fim\":\"" + fim + "\""));
    }

    /** Matricula e devolve o id da matrícula (confere o 201). */
    public long matricula(long turma, String email, Instant inicio, Instant fim) {
        return CursoITHelper.idCriado(matricular(turma, email, inicio, fim));
    }

    public void cancelar(long matricula) {
        assertThat(adm.enviar("POST", "/api/adm/matriculas/" + matricula + "/cancelamento", null))
                .hasStatus(HttpStatus.OK);
    }

    public record Estrutura(long curso, long turma, long modulo, long aulaPaga, long aulaGratuita) {}
}
