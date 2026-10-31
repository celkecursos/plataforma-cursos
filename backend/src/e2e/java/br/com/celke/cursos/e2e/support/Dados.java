package br.com.celke.cursos.e2e.support;

import java.util.concurrent.ThreadLocalRandom;

/** Identificadores únicos dos dados criados pelas jornadas (prefixo {@code e2e-}). */
public final class Dados {

    private Dados() {}

    public static String stamp() {
        return "e2e-" + Long.toString(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE), 36);
    }

    public static String email() {
        return stamp() + "@teste.celke.uno";
    }
}
