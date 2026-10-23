package br.com.celke.cursos.video.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Formatos aceitos no envio, cada um com a assinatura que o conteúdo real precisa ter. A extensão e o
 * {@code Content-Type} informados pelo navegador não bastam: o início do arquivo é conferido depois
 * do envio, e o ffprobe ainda analisa o arquivo antes da conversão.
 */
public enum FormatoVideo {
    MP4("video/mp4", Familia.ISO),
    MOV("video/quicktime", Familia.ISO),
    MKV("video/x-matroska", Familia.EBML),
    WEBM("video/webm", Familia.EBML);

    /** Bytes lidos do início do arquivo para conferir a assinatura. */
    public static final int BYTES_ASSINATURA = 12;

    private static final byte[] EBML = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};
    private static final byte[] FTYP = {'f', 't', 'y', 'p'};

    private final String contentType;
    private final Familia familia;

    FormatoVideo(String contentType, Familia familia) {
        this.contentType = contentType;
        this.familia = familia;
    }

    public static Optional<FormatoVideo> doContentType(String contentType) {
        return Arrays.stream(values())
                .filter(f -> f.contentType.equals(contentType))
                .findFirst();
    }

    public String contentType() {
        return contentType;
    }

    /**
     * Confere o início do arquivo: MP4 e MOV têm a caixa {@code ftyp} no byte 4; MKV e WebM começam com
     * o cabeçalho EBML.
     */
    public boolean assinaturaConfere(byte[] inicio) {
        return switch (familia) {
            case ISO -> inicio.length >= 8 && Arrays.equals(inicio, 4, 8, FTYP, 0, 4);
            case EBML -> inicio.length >= 4 && Arrays.equals(inicio, 0, 4, EBML, 0, 4);
        };
    }

    private enum Familia {
        ISO,
        EBML
    }
}
