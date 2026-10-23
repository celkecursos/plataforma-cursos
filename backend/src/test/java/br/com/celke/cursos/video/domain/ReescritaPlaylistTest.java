package br.com.celke.cursos.video.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReescritaPlaylistTest {

    /** Master como o ffmpeg do worker gera. */
    private static final String MASTER = """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-STREAM-INF:BANDWIDTH=266709,AVERAGE-BANDWIDTH=266709,RESOLUTION=1280x720,CODECS="avc1.64001f,mp4a.40.2"
            720p/index.m3u8

            #EXT-X-STREAM-INF:BANDWIDTH=510559,AVERAGE-BANDWIDTH=510559,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2"
            1080p/index.m3u8
            """;

    private static final String VARIANTE = """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:0
            #EXT-X-PLAYLIST-TYPE:VOD
            #EXT-X-INDEPENDENT-SEGMENTS
            #EXTINF:6.000000,
            seg_0000.ts
            #EXTINF:2.500000,
            seg_0001.ts
            #EXT-X-ENDLIST
            """;

    @Test
    @DisplayName("master: as variantes passam a apontar para a API, relativas à master")
    void master() {
        String reescrita = ReescritaPlaylist.master(MASTER, Set.of("720p", "1080p"));

        assertThat(reescrita)
                .contains("\nvideo/720p.m3u8\n")
                .contains("\nvideo/1080p.m3u8\n")
                .doesNotContain("index.m3u8")
                .contains("RESOLUTION=1920x1080")
                .startsWith("#EXTM3U\n")
                .endsWith("\n");
    }

    @Test
    @DisplayName("master com variante que o vídeo não tem é recusada")
    void masterComVarianteDesconhecida() {
        assertThatThrownBy(() -> ReescritaPlaylist.master(MASTER, Set.of("720p")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Referência inesperada na master playlist: 1080p/index.m3u8");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://outro.site/x.m3u8", "../../segredo/index.m3u8", "720p/../index.m3u8"})
    @DisplayName("master com referência fora do formato do worker é recusada")
    void masterAdulterada(String uri) {
        String adulterada = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\n" + uri + "\n";

        assertThatThrownBy(() -> ReescritaPlaylist.master(adulterada, Set.of("720p")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("variante: cada segmento vira a URL assinada; as tags ficam como estão")
    void variante() {
        String reescrita = ReescritaPlaylist.variante(VARIANTE, seg -> "https://media.celke.uno/assinado/" + seg);

        assertThat(reescrita.lines())
                .contains(
                        "https://media.celke.uno/assinado/seg_0000.ts",
                        "https://media.celke.uno/assinado/seg_0001.ts",
                        "#EXTINF:2.500000,",
                        "#EXT-X-ENDLIST")
                .doesNotContain("seg_0000.ts");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../outro/seg_0000.ts", "seg_0000.mp4", "https://x/seg_0000.ts", "seg_00.ts"})
    @DisplayName("variante com segmento fora do formato do worker é recusada (sem assinar nada)")
    void varianteAdulterada(String uri) {
        String adulterada = "#EXTM3U\n#EXTINF:6.0,\n" + uri + "\n";

        assertThatThrownBy(() -> ReescritaPlaylist.variante(adulterada, seg -> {
                    throw new AssertionError("não deveria assinar " + seg);
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Referência inesperada na playlist da variante: " + uri);
    }
}
