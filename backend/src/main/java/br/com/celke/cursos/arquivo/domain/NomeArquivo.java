package br.com.celke.cursos.arquivo.domain;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Nome sugerido no download, seguro para o cabeçalho {@code Content-Disposition}: sem caminho, sem acento,
 * só letras, números, ponto, hífen e sublinhado, e com a extensão do formato real.
 */
public final class NomeArquivo {

    static final int TAMANHO_MAXIMO = 100;

    private NomeArquivo() {}

    public static String de(String informado, FormatoArquivo formato) {
        String nome = informado == null ? "" : informado.strip();
        // Sem caminho: fica só o que vem depois da última barra
        nome = nome.substring(Math.max(nome.lastIndexOf('/'), nome.lastIndexOf('\\')) + 1);
        int ponto = nome.lastIndexOf('.');
        String base = ponto > 0 ? nome.substring(0, ponto) : nome;
        base = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^[-.]+|[-.]+$", "");
        if (base.isEmpty()) {
            base = "arquivo";
        }
        String extensao = "." + extensao(formato);
        if (base.length() > TAMANHO_MAXIMO - extensao.length()) {
            base = base.substring(0, TAMANHO_MAXIMO - extensao.length());
        }
        return base + extensao;
    }

    private static String extensao(FormatoArquivo formato) {
        return switch (formato) {
            case TEXTO -> "txt";
            default -> formato.name().toLowerCase(Locale.ROOT);
        };
    }
}
