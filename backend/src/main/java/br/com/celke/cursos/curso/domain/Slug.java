package br.com.celke.cursos.curso.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Slug do curso na URL do site: letras minúsculas sem acento, números e hífens entre palavras. */
public final class Slug {

    public static final int TAMANHO_MAXIMO = 80;
    public static final String FORMATO = "^[a-z0-9]+(-[a-z0-9]+)*$";

    private static final Pattern VALIDO = Pattern.compile(FORMATO);
    private static final Pattern ACENTOS = Pattern.compile("\\p{M}+");
    private static final Pattern SEPARADORES = Pattern.compile("[^a-z0-9]+");

    private Slug() {}

    /** Gera o slug a partir do título: "Java & Spring: do Zero!" → "java-spring-do-zero". */
    public static String gerar(String titulo) {
        String semAcento = ACENTOS.matcher(Normalizer.normalize(titulo, Normalizer.Form.NFD))
                .replaceAll("");
        String slug = SEPARADORES.matcher(semAcento.toLowerCase(Locale.ROOT)).replaceAll("-");
        slug = slug.replaceAll("^-+|-+$", "");
        if (slug.length() > TAMANHO_MAXIMO) {
            slug = slug.substring(0, TAMANHO_MAXIMO).replaceAll("-+$", "");
        }
        return slug;
    }

    public static boolean valido(String slug) {
        return slug != null
                && slug.length() <= TAMANHO_MAXIMO
                && VALIDO.matcher(slug).matches();
    }
}
