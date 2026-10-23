package br.com.celke.cursos.certificado.domain;

import java.util.regex.Pattern;

/**
 * Nome completo confirmado pelo aluno para o certificado: letras (acentuadas inclusive), espaço, hífen e
 * apóstrofo, com pelo menos nome e sobrenome. Espaços repetidos viram um só.
 */
public final class NomeCertificado {

    private static final Pattern PERMITIDO = Pattern.compile("[\\p{L}' -]+");

    private NomeCertificado() {}

    /** Nome normalizado, ou nulo se não é um nome completo aceitável. */
    public static String normalizar(String informado) {
        if (informado == null) {
            return null;
        }
        String nome = informado.strip().replaceAll("\\s+", " ");
        if (nome.length() < 5 || nome.length() > 150 || !PERMITIDO.matcher(nome).matches() || !nome.contains(" ")) {
            return null;
        }
        return nome;
    }
}
