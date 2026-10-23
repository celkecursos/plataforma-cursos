package br.com.celke.cursos.shared.fila;

import java.time.Duration;

/** Atraso exponencial entre tentativas: base, 2×base, 4×base... limitado ao máximo. */
public final class Backoff {

    private final Duration base;
    private final Duration maximo;

    public Backoff(Duration base, Duration maximo) {
        if (base.isNegative() || base.isZero() || maximo.compareTo(base) < 0) {
            throw new IllegalArgumentException("Backoff exige base positiva e máximo maior ou igual à base.");
        }
        this.base = base;
        this.maximo = maximo;
    }

    /** Atraso depois da falha da tentativa informada (a primeira tentativa é 1). */
    public Duration atraso(int tentativa) {
        if (tentativa < 1) {
            throw new IllegalArgumentException("A tentativa começa em 1.");
        }
        // A partir de 2^62 a multiplicação estoura; muito antes disso o máximo já foi atingido
        int expoente = Math.min(tentativa - 1, 62);
        long fator = 1L << expoente;
        long limite = maximo.toMillis() / base.toMillis();
        if (fator >= limite) {
            return maximo;
        }
        return base.multipliedBy(fator);
    }
}
