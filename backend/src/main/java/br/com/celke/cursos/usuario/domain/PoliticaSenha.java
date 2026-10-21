package br.com.celke.cursos.usuario.domain;

/**
 * Regras de senha: tamanho mínimo e máximo (o máximo evita hash de textos enormes). Não exige
 * mistura de caracteres: comprimento protege mais e incomoda menos.
 */
public final class PoliticaSenha {

    public static final int MINIMO_ADMIN = 12;
    public static final int MAXIMO = 128;

    private PoliticaSenha() {}

    /** Devolve a mensagem do problema, ou {@code null} se a senha atende à política. */
    public static String validar(String senha, int minimo) {
        if (senha == null || senha.isBlank()) {
            return "A senha é obrigatória.";
        }
        if (senha.length() < minimo) {
            return "A senha deve ter pelo menos " + minimo + " caracteres.";
        }
        if (senha.length() > MAXIMO) {
            return "A senha deve ter no máximo " + MAXIMO + " caracteres.";
        }
        return null;
    }
}
