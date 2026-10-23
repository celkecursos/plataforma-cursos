package br.com.celke.cursos.arquivo.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Formatos aceitos nos arquivos de apoio, cada um conferido pelo conteúdo real depois do envio (o tipo
 * informado pelo navegador não basta): ZIP e PDF pela assinatura, texto por não ter byte nulo no início.
 */
public enum FormatoArquivo {
    ZIP("application/zip"),
    PDF("application/pdf"),
    TEXTO("text/plain");

    /** Bytes lidos do início do arquivo para a conferência. */
    public static final int BYTES_CONFERENCIA = 512;

    private static final byte[] ZIP_LOCAL = {'P', 'K', 3, 4};
    private static final byte[] ZIP_VAZIO = {'P', 'K', 5, 6};
    private static final byte[] PDF_INICIO = {'%', 'P', 'D', 'F', '-'};

    private final String contentType;

    FormatoArquivo(String contentType) {
        this.contentType = contentType;
    }

    public static Optional<FormatoArquivo> doContentType(String contentType) {
        return Arrays.stream(values())
                .filter(f -> f.contentType.equals(contentType))
                .findFirst();
    }

    public String contentType() {
        return contentType;
    }

    public boolean conteudoConfere(byte[] inicio) {
        return switch (this) {
            case ZIP -> comeca(inicio, ZIP_LOCAL) || comeca(inicio, ZIP_VAZIO);
            case PDF -> comeca(inicio, PDF_INICIO);
            case TEXTO -> inicio.length > 0 && !temByteNulo(inicio);
        };
    }

    private static boolean comeca(byte[] inicio, byte[] assinatura) {
        return inicio.length >= assinatura.length
                && Arrays.equals(inicio, 0, assinatura.length, assinatura, 0, assinatura.length);
    }

    private static boolean temByteNulo(byte[] inicio) {
        for (byte b : inicio) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }
}
