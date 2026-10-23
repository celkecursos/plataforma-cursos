package br.com.celke.cursos.usuario.domain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Regras de senha: tamanho mínimo e máximo (o máximo evita hash de textos enormes), fora da lista de
 * senhas comuns e vazadas, sem o nome ou o e-mail de quem a usa e sem sequência ou repetição óbvia.
 * Não exige mistura de caracteres: comprimento e imprevisibilidade protegem mais e incomodam menos.
 */
public final class PoliticaSenha {

    public static final int MINIMO_ALUNO = 10;
    public static final int MINIMO_ADMIN = 12;
    public static final int MAXIMO = 128;

    public static final String COMUM = "Esta senha é muito comum ou já apareceu em vazamentos. Escolha outra.";
    public static final String PESSOAL = "A senha não pode conter o seu nome ou o seu e-mail.";
    public static final String PREVISIVEL = "A senha é fácil de adivinhar (sequência ou repetição). Escolha outra.";

    /** Lista de senhas comuns e vazadas, com 10 a 128 caracteres, em minúsculas (SecLists). */
    static final String LISTA = "/seguranca/senhas-comuns.txt";

    /** Trechos de nome e e-mail menores que isso não contam (ex.: "ana" em "banana"). */
    static final int TAMANHO_MINIMO_TRECHO = 4;

    /** Sequências de teclado e de alfabeto; senha inteira dentro de uma delas (ou do reverso) é previsível. */
    private static final List<String> SEQUENCIAS = List.of(
            "0123456789".repeat(4),
            "abcdefghijklmnopqrstuvwxyz".repeat(2),
            "qwertyuiopasdfghjklzxcvbnm".repeat(2),
            "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik9ol0p",
            "qazwsxedcrfvtgbyhnujmikolp");

    /** Um bloco de até 4 caracteres repetido do começo ao fim ("abcabcabcabc"). */
    private static final Pattern BLOCO_REPETIDO = Pattern.compile("(.{1,4})\\1+");

    private PoliticaSenha() {}

    /**
     * Devolve a mensagem do problema, ou {@code null} se a senha atende à política.
     *
     * @param pessoais nome, e-mail e outros dados da pessoa que a senha não pode conter
     */
    public static String validar(String senha, int minimo, String... pessoais) {
        if (senha == null || senha.isBlank()) {
            return "A senha é obrigatória.";
        }
        if (senha.length() < minimo) {
            return "A senha deve ter pelo menos " + minimo + " caracteres.";
        }
        if (senha.length() > MAXIMO) {
            return "A senha deve ter no máximo " + MAXIMO + " caracteres.";
        }
        String normalizada = senha.toLowerCase(Locale.ROOT);
        if (Comuns.LISTA.contains(normalizada)) {
            return COMUM;
        }
        if (contemPessoal(normalizada, pessoais)) {
            return PESSOAL;
        }
        if (previsivel(normalizada)) {
            return PREVISIVEL;
        }
        return null;
    }

    static boolean contemPessoal(String senha, String... pessoais) {
        return Arrays.stream(pessoais)
                .filter(valor -> valor != null && !valor.isBlank())
                .flatMap(valor -> trechos(valor.toLowerCase(Locale.ROOT)).stream())
                .anyMatch(senha::contains);
    }

    /** Partes do dado que contam: o e-mail inteiro, o usuário antes do @ e cada palavra do nome. */
    private static Set<String> trechos(String valor) {
        Set<String> trechos = new HashSet<>();
        String local = valor.contains("@") ? valor.substring(0, valor.indexOf('@')) : valor;
        trechos.add(valor.strip());
        trechos.add(local.strip());
        trechos.addAll(Arrays.asList(local.split("[^\\p{L}\\p{N}]+")));
        trechos.removeIf(t -> t.length() < TAMANHO_MINIMO_TRECHO);
        return trechos;
    }

    static boolean previsivel(String senha) {
        if (senha.chars().distinct().count() <= 2
                || BLOCO_REPETIDO.matcher(senha).matches()) {
            return true;
        }
        String reversa = new StringBuilder(senha).reverse().toString();
        return SEQUENCIAS.stream().anyMatch(s -> s.contains(senha) || s.contains(reversa));
    }

    /** Lista carregada uma vez, no primeiro uso. */
    private static final class Comuns {

        static final Set<String> LISTA = carregar();

        private static Set<String> carregar() {
            try (InputStream entrada = PoliticaSenha.class.getResourceAsStream(PoliticaSenha.LISTA)) {
                if (entrada == null) {
                    throw new IllegalStateException("Lista de senhas comuns não encontrada: " + PoliticaSenha.LISTA);
                }
                Set<String> senhas = new HashSet<>(160_000);
                try (BufferedReader leitor =
                        new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
                    leitor.lines().map(String::strip).filter(l -> !l.isEmpty()).forEach(senhas::add);
                }
                return Set.copyOf(senhas);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
