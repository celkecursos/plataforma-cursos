package br.com.celke.cursos.video.domain;

import java.util.List;

/**
 * Uma resolução do HLS, com o teto de taxa de bits do CRF ({@code maxrate}) e o buffer do controle de
 * taxa ({@code bufsize}, o dobro).
 */
public record Variante(int altura, int maxrateKbps) {

    /** Alturas-alvo da plataforma. */
    public static final List<Variante> PADRAO = List.of(new Variante(720, 2800), new Variante(1080, 5000));

    /** Teto para origem menor que 720p: proporcional à altura, com mínimo de 600 kbps. */
    static final int KBPS_POR_LINHA = 4;

    public String nome() {
        return altura + "p";
    }

    public int bufsizeKbps() {
        return maxrateKbps * 2;
    }

    /**
     * Variantes para uma origem: as alturas-alvo que não passam da altura de origem (nunca aumenta a
     * resolução). Origem abaixo de 720p gera uma variante só, na própria altura (arredondada para par,
     * exigência do H.264 com yuv420p).
     */
    public static List<Variante> paraOrigem(int alturaOrigem) {
        if (alturaOrigem < 2) {
            throw new IllegalArgumentException("Altura de origem inválida: " + alturaOrigem);
        }
        List<Variante> cabem =
                PADRAO.stream().filter(v -> v.altura() <= alturaOrigem).toList();
        if (!cabem.isEmpty()) {
            return cabem;
        }
        int altura = alturaOrigem - alturaOrigem % 2;
        return List.of(new Variante(altura, Math.max(600, altura * KBPS_POR_LINHA)));
    }
}
