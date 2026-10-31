package br.com.celke.cursos.video.infra;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.video.domain.Variante;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ComandosFfmpegTest {

    private static final Path ENTRADA = Path.of("/tmp/trabalho/original");
    private final ComandosFfmpeg comandos = new ComandosFfmpeg("ffmpeg", "ffprobe", "veryfast", 24);
    private final List<Variante> duas = Variante.paraOrigem(1080);

    /** Valor do argumento logo depois da opção. */
    private static String valor(List<String> args, String opcao) {
        return args.get(args.indexOf(opcao) + 1);
    }

    @Test
    @DisplayName("ffprobe devolve formato e faixas em JSON")
    void analisar() {
        assertThat(comandos.analisar(ENTRADA))
                .containsExactly(
                        "ffprobe",
                        "-v",
                        "error",
                        "-print_format",
                        "json",
                        "-show_format",
                        "-show_streams",
                        ENTRADA.toString());
    }

    @Nested
    class ConverterHls {

        @Test
        @DisplayName("H.264 com CRF e preset configurados, teto de taxa por variante e segmentos de 6 s")
        void parametros() {
            List<String> args = comandos.converterHls(ENTRADA, duas, true);

            assertThat(args.getFirst()).isEqualTo("ffmpeg");
            assertThat(valor(args, "-i")).isEqualTo(ENTRADA.toString());
            assertThat(valor(args, "-c:v")).isEqualTo("libx264");
            assertThat(valor(args, "-preset")).isEqualTo("veryfast");
            assertThat(valor(args, "-crf")).isEqualTo("24");
            assertThat(valor(args, "-maxrate:v:0")).isEqualTo("2800k");
            assertThat(valor(args, "-bufsize:v:0")).isEqualTo("5600k");
            assertThat(valor(args, "-maxrate:v:1")).isEqualTo("5000k");
            assertThat(valor(args, "-bufsize:v:1")).isEqualTo("10000k");
            assertThat(valor(args, "-hls_time")).isEqualTo("6");
            assertThat(valor(args, "-force_key_frames")).isEqualTo("expr:gte(t,n_forced*6)");
            assertThat(valor(args, "-hls_playlist_type")).isEqualTo("vod");
            assertThat(valor(args, "-master_pl_name")).isEqualTo("master.m3u8");
            assertThat(valor(args, "-hls_segment_filename")).isEqualTo("%v/seg_%04d.ts");
            assertThat(args.getLast()).isEqualTo("%v/index.m3u8");
            assertThat(args).contains("-nostdin");
        }

        @Test
        @DisplayName("com áudio: AAC 128k estéreo, uma faixa de áudio por variante")
        void comAudio() {
            List<String> args = comandos.converterHls(ENTRADA, duas, true);

            assertThat(valor(args, "-c:a")).isEqualTo("aac");
            assertThat(valor(args, "-b:a")).isEqualTo("128k");
            assertThat(valor(args, "-ac")).isEqualTo("2");
            assertThat(args.stream().filter("0:a:0"::equals)).hasSize(2);
            assertThat(valor(args, "-var_stream_map")).isEqualTo("v:0,a:0,name:720p v:1,a:1,name:1080p");
            assertThat(valor(args, "-filter_complex"))
                    .isEqualTo("[0:v]split=2[v0][v1];[v0]scale=-2:720[s0];[v1]scale=-2:1080[s1]");
        }

        @Test
        @DisplayName("sem áudio: nenhuma opção de áudio e o mapa só com vídeo")
        void semAudio() {
            List<String> args = comandos.converterHls(ENTRADA, List.of(new Variante(480, 1920)), false);

            assertThat(args).doesNotContain("-c:a", "0:a:0");
            assertThat(valor(args, "-var_stream_map")).isEqualTo("v:0,name:480p");
            assertThat(valor(args, "-filter_complex")).isEqualTo("[0:v]split=1[v0];[v0]scale=-2:480[s0]");
            assertThat(valor(args, "-map")).isEqualTo("[s0]");
        }

        @Test
        @DisplayName("nome de arquivo com caracteres de shell vira um argumento só, sem interpretação")
        void semShell() {
            Path perigoso = Path.of("/tmp/a; rm -rf ~ $(whoami).mp4");

            List<String> args = comandos.converterHls(perigoso, duas, true);

            assertThat(valor(args, "-i")).isEqualTo(perigoso.toString());
            assertThat(args).noneMatch(a -> a.equals("sh") || a.equals("-c") || a.equals("bash"));
        }
    }

    @Nested
    class Thumbnail {

        @Test
        @DisplayName("quadro a 10% da duração, com até 720 linhas")
        void dezPorCento() {
            List<String> args = comandos.thumbnail(ENTRADA, 30, 1080);

            assertThat(valor(args, "-ss")).isEqualTo("3.000");
            assertThat(valor(args, "-vf")).isEqualTo("scale=-2:720");
            assertThat(valor(args, "-frames:v")).isEqualTo("1");
            assertThat(args.getLast()).isEqualTo("thumbnail.jpg");
        }

        @Test
        @DisplayName("vídeo longo: no máximo aos 10 s; origem pequena não é ampliada (altura par)")
        void limites() {
            List<String> args = comandos.thumbnail(ENTRADA, 3600, 361);

            assertThat(valor(args, "-ss")).isEqualTo("10.000");
            assertThat(valor(args, "-vf")).isEqualTo("scale=-2:360");
        }
    }
}
