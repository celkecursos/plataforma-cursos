package br.com.celke.cursos.video.domain;

import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reescreve as playlists HLS geradas pelo worker para entrega ao aluno. O bucket é privado: a master
 * aponta para as playlists das variantes servidas pela API, e cada variante aponta para os segmentos
 * por URLs pré-assinadas. Só nomes no formato gerado pelo worker são aceitos; qualquer outra referência
 * indica playlist adulterada.
 */
public final class ReescritaPlaylist {

    private static final Pattern VARIANTE = Pattern.compile("(\\d{1,4}p)/index\\.m3u8");
    private static final Pattern SEGMENTO = Pattern.compile("seg_\\d{4,6}\\.ts");

    private ReescritaPlaylist() {}

    /**
     * Master: {@code 720p/index.m3u8} vira {@code video/720p.m3u8}, relativo ao endereço da master na
     * API ({@code /api/aulas/<id>/video.m3u8}).
     */
    public static String master(String conteudo, Set<String> variantes) {
        return reescrever(conteudo, uri -> {
            Matcher m = VARIANTE.matcher(uri);
            if (!m.matches() || !variantes.contains(m.group(1))) {
                throw new IllegalStateException("Referência inesperada na master playlist: " + uri);
            }
            return "video/" + m.group(1) + ".m3u8";
        });
    }

    /** Variante: cada segmento vira a URL que {@code assinar} devolve para o nome dele. */
    public static String variante(String conteudo, UnaryOperator<String> assinar) {
        return reescrever(conteudo, uri -> {
            if (!SEGMENTO.matcher(uri).matches()) {
                throw new IllegalStateException("Referência inesperada na playlist da variante: " + uri);
            }
            return assinar.apply(uri);
        });
    }

    /** Linhas vazias e de tag ({@code #...}) ficam como estão; as outras são URIs. */
    private static String reescrever(String conteudo, UnaryOperator<String> uri) {
        return conteudo.lines()
                        .map(linha -> linha.isBlank() || linha.startsWith("#") ? linha : uri.apply(linha.strip()))
                        .collect(Collectors.joining("\n"))
                + "\n";
    }
}
