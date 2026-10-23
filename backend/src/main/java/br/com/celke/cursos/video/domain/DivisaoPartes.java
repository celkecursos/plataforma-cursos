package br.com.celke.cursos.video.domain;

/**
 * Divisão do arquivo em partes para o upload multipart. O S3 aceita até 10.000 partes, e toda parte,
 * menos a última, precisa ter pelo menos 5 MiB.
 *
 * @param tamanhoParte bytes de cada parte (a última pode ser menor)
 * @param quantidade número de partes
 */
public record DivisaoPartes(long tamanhoParte, int quantidade) {

    public static final long PARTE_PADRAO = 16L * 1024 * 1024;
    public static final int MAXIMO_PARTES = 10_000;
    private static final long MIB = 1024L * 1024;

    /** Partes de 16 MiB; arquivo que passaria de 10.000 partes usa partes maiores (múltiplos de 1 MiB). */
    public static DivisaoPartes de(long tamanho) {
        if (tamanho <= 0) {
            throw new IllegalArgumentException("O tamanho precisa ser positivo.");
        }
        long parte = PARTE_PADRAO;
        if (divisaoParaCima(tamanho, parte) > MAXIMO_PARTES) {
            parte = divisaoParaCima(divisaoParaCima(tamanho, MAXIMO_PARTES), MIB) * MIB;
        }
        return new DivisaoPartes(parte, (int) divisaoParaCima(tamanho, parte));
    }

    private static long divisaoParaCima(long a, long b) {
        return (a + b - 1) / b;
    }
}
