package br.com.celke.cursos.progresso.domain;

/** Percentual de aulas vistas na turma, arredondado para baixo: 100% só com todas as aulas vistas. */
public final class PercentualConclusao {

    private PercentualConclusao() {}

    /** Turma sem aulas fica em 0%. */
    public static int de(int vistas, int total) {
        if (vistas < 0 || total < 0 || vistas > total) {
            throw new IllegalArgumentException("Contagem inválida: %d de %d.".formatted(vistas, total));
        }
        return total == 0 ? 0 : (int) ((long) vistas * 100 / total);
    }
}
