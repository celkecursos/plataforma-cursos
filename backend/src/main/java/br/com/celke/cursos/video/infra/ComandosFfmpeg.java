package br.com.celke.cursos.video.infra;

import br.com.celke.cursos.video.domain.Variante;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Argumentos do ffprobe e do ffmpeg, sempre como lista: cada item vira um argumento do processo, sem
 * shell no meio, então um nome de arquivo nunca é interpretado como comando.
 */
public final class ComandosFfmpeg {

    /** Duração dos segmentos HLS, em segundos. */
    static final int SEGUNDOS_SEGMENTO = 6;

    static final String MASTER = "master.m3u8";
    static final String THUMBNAIL = "thumbnail.jpg";
    static final int ALTURA_THUMBNAIL = 720;

    private final String ffmpeg;
    private final String ffprobe;
    private final String preset;
    private final int crf;

    public ComandosFfmpeg(String ffmpeg, String ffprobe, String preset, int crf) {
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
        this.preset = preset;
        this.crf = crf;
    }

    /** Formato e faixas do arquivo em JSON. */
    public List<String> analisar(Path entrada) {
        return List.of(
                ffprobe, "-v", "error", "-print_format", "json", "-show_format", "-show_streams", entrada.toString());
    }

    /**
     * HLS com uma variante por altura numa passada só (o vídeo é decodificado uma vez): H.264 com CRF e
     * teto de taxa por variante, áudio AAC 128k, segmentos de 6 s com quadro-chave forçado no início de
     * cada um. Roda no diretório de saída: gera {@code master.m3u8} e {@code <altura>p/index.m3u8} com os
     * segmentos.
     */
    public List<String> converterHls(Path entrada, List<Variante> variantes, boolean temAudio) {
        int n = variantes.size();
        List<String> args = new ArrayList<>(List.of(ffmpeg, "-hide_banner", "-nostdin", "-y", "-loglevel", "error"));
        args.addAll(List.of("-i", entrada.toString()));
        args.addAll(List.of("-filter_complex", filtroEscala(variantes)));
        for (int i = 0; i < n; i++) {
            args.addAll(List.of("-map", "[s" + i + "]"));
            if (temAudio) {
                args.addAll(List.of("-map", "0:a:0"));
            }
        }
        args.addAll(List.of(
                "-c:v",
                "libx264",
                "-preset",
                preset,
                "-crf",
                String.valueOf(crf),
                "-profile:v",
                "high",
                "-pix_fmt",
                "yuv420p"));
        for (int i = 0; i < n; i++) {
            Variante v = variantes.get(i);
            args.addAll(List.of("-maxrate:v:" + i, v.maxrateKbps() + "k", "-bufsize:v:" + i, v.bufsizeKbps() + "k"));
        }
        args.addAll(List.of("-force_key_frames", "expr:gte(t,n_forced*" + SEGUNDOS_SEGMENTO + ")"));
        if (temAudio) {
            args.addAll(List.of("-c:a", "aac", "-b:a", "128k", "-ac", "2", "-ar", "48000"));
        }
        args.addAll(List.of(
                "-f",
                "hls",
                "-hls_time",
                String.valueOf(SEGUNDOS_SEGMENTO),
                "-hls_playlist_type",
                "vod",
                "-hls_flags",
                "independent_segments",
                "-hls_segment_filename",
                "%v/seg_%04d.ts",
                "-master_pl_name",
                MASTER,
                "-var_stream_map",
                mapaVariantes(variantes, temAudio),
                "%v/index.m3u8"));
        return args;
    }

    /** Um quadro em JPEG, a 10% da duração (no máximo aos 10 s), com até 720 linhas. */
    public List<String> thumbnail(Path entrada, double duracao, int alturaOrigem) {
        double instante = Math.min(duracao * 0.1, 10);
        int altura = Math.min(ALTURA_THUMBNAIL, alturaOrigem - alturaOrigem % 2);
        return List.of(
                ffmpeg,
                "-hide_banner",
                "-nostdin",
                "-y",
                "-loglevel",
                "error",
                "-ss",
                String.format(Locale.ROOT, "%.3f", instante),
                "-i",
                entrada.toString(),
                "-frames:v",
                "1",
                "-vf",
                "scale=-2:" + altura,
                "-q:v",
                "3",
                THUMBNAIL);
    }

    /** {@code [0:v]split=2[v0][v1];[v0]scale=-2:720[s0];[v1]scale=-2:1080[s1]} */
    static String filtroEscala(List<Variante> variantes) {
        int n = variantes.size();
        String saidas = IntStream.range(0, n).mapToObj(i -> "[v" + i + "]").collect(Collectors.joining());
        String escalas = IntStream.range(0, n)
                .mapToObj(i ->
                        "[v%d]scale=-2:%d[s%d]".formatted(i, variantes.get(i).altura(), i))
                .collect(Collectors.joining(";"));
        return "[0:v]split=" + n + saidas + ";" + escalas;
    }

    /** {@code v:0,a:0,name:720p v:1,a:1,name:1080p} (sem a faixa de áudio quando a origem não tem). */
    static String mapaVariantes(List<Variante> variantes, boolean temAudio) {
        return IntStream.range(0, variantes.size())
                .mapToObj(i -> (temAudio ? "v:%d,a:%d,name:%s" : "v:%d,name:%3$s")
                        .formatted(i, i, variantes.get(i).nome()))
                .collect(Collectors.joining(" "));
    }
}
