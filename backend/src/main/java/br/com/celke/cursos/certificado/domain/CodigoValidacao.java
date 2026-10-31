package br.com.celke.cursos.certificado.domain;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Código público que valida o certificado: 12 caracteres aleatórios em três grupos ({@code K7QM-2XRP-H9TD}),
 * sem letras e números que se confundem (0/O, 1/I/L, U). São 60 bits de acaso: não dá para adivinhar um
 * código válido.
 */
public final class CodigoValidacao {

    static final String ALFABETO = "23456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final Pattern FORMATO =
            Pattern.compile("[" + ALFABETO + "]{4}-[" + ALFABETO + "]{4}-[" + ALFABETO + "]{4}");
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private CodigoValidacao() {}

    public static String gerar() {
        StringBuilder codigo = new StringBuilder(14);
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) {
                codigo.append('-');
            }
            codigo.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
        }
        return codigo.toString();
    }

    /** Código digitado normalizado (maiúsculas, sem espaços), ou nulo se não tem o formato. */
    public static String normalizar(String digitado) {
        if (digitado == null) {
            return null;
        }
        String codigo = digitado.strip().toUpperCase(Locale.ROOT);
        return FORMATO.matcher(codigo).matches() ? codigo : null;
    }
}
