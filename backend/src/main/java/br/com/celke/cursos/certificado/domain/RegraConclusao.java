package br.com.celke.cursos.certificado.domain;

/** O aluno concluiu a turma quando o percentual de aulas vistas alcança o mínimo da turma. */
public final class RegraConclusao {

    private RegraConclusao() {}

    /**
     * @param percentualVisto aulas vistas, de 0 a 100 (arredondado para baixo)
     * @param minimo percentual exigido pela turma, de 1 a 100
     * @param totalAulas aulas ativas na turma: turma sem aulas nunca é concluída
     */
    public static boolean concluiu(int percentualVisto, int minimo, int totalAulas) {
        return totalAulas > 0 && percentualVisto >= minimo;
    }
}
