package br.com.celke.cursos.video.infra;

import br.com.celke.cursos.shared.fila.ErroPermanenteException;
import br.com.celke.cursos.video.application.VideoProperties;
import br.com.celke.cursos.video.domain.Variante;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Executa o ffprobe e o ffmpeg com {@link ProcessBuilder} (lista de argumentos, sem shell). A saída de
 * erro vai para um arquivo, e o fim dela entra na mensagem quando o processo falha. Enquanto espera,
 * chama {@code aCadaMinuto} (o worker renova o lease do job, para uma conversão longa não parecer
 * travada).
 */
@Component
public class Ffmpeg {

    static final Duration INTERVALO = Duration.ofMinutes(1);
    static final Duration LIMITE_ANALISE = Duration.ofMinutes(2);
    /** Fim da saída de erro guardado na mensagem de falha. */
    static final int CARACTERES_ERRO = 1500;

    static final String ARQUIVO_ERRO = "ffmpeg.log";

    private final ComandosFfmpeg comandos;
    private final List<String> prefixo;
    private final JsonMapper jsonMapper;

    public Ffmpeg(VideoProperties props, JsonMapper jsonMapper) {
        this.comandos = new ComandosFfmpeg(props.ffmpeg(), props.ffprobe(), props.preset(), props.crf());
        this.prefixo = props.prefixoComando();
        this.jsonMapper = jsonMapper;
    }

    /** Analisa o original; arquivo que o ffprobe não reconhece é erro permanente. */
    public AnaliseVideo analisar(Path entrada, Path trabalho) {
        Path json = trabalho.resolve("ffprobe.json");
        int codigo = executar(comandos.analisar(entrada), trabalho, json, LIMITE_ANALISE, () -> {});
        if (codigo != 0) {
            throw new ErroPermanenteException(
                    "O arquivo não é um vídeo válido: " + fimDoErro(trabalho, "ffprobe.json"));
        }
        try {
            return AnaliseVideo.de(jsonMapper.readValue(json.toFile(), AnaliseVideo.SaidaFfprobe.class));
        } catch (ErroPermanenteException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ErroPermanenteException("Não foi possível analisar o vídeo.");
        }
    }

    /**
     * Gera o HLS e a thumbnail em {@code saida}. O tempo limite é generoso (vinte vezes a duração, mais
     * dez minutos): só pega o ffmpeg travado.
     */
    public void converter(
            Path entrada, Path saida, List<Variante> variantes, AnaliseVideo analise, Runnable aCadaMinuto) {
        criarDiretorios(saida, variantes);
        Duration limite = Duration.ofMinutes(10).plusSeconds((long) Math.ceil(analise.duracao() * 20));
        exigirSucesso(
                executar(
                        comandos.converterHls(entrada, variantes, analise.temAudio()),
                        saida,
                        null,
                        limite,
                        aCadaMinuto),
                saida,
                "conversão para HLS");
        exigirSucesso(
                executar(
                        comandos.thumbnail(entrada, analise.duracao(), analise.altura()),
                        saida,
                        null,
                        LIMITE_ANALISE,
                        aCadaMinuto),
                saida,
                "thumbnail");
        // A saída de erro não é publicada com o HLS
        apagar(saida.resolve(ARQUIVO_ERRO));
    }

    /**
     * Roda o comando no diretório informado; a saída padrão vai para {@code saidaPadrao} (ou é
     * descartada) e a de erro para {@code ffmpeg.log} (ou {@code <saidaPadrao>.err}). Devolve o código
     * de saída.
     */
    int executar(List<String> args, Path diretorio, Path saidaPadrao, Duration limite, Runnable aCadaMinuto) {
        List<String> comando = new ArrayList<>(prefixo);
        comando.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(comando)
                .directory(diretorio.toFile())
                .redirectError(arquivoErro(diretorio, saidaPadrao).toFile())
                .redirectOutput(
                        saidaPadrao == null
                                ? ProcessBuilder.Redirect.DISCARD
                                : ProcessBuilder.Redirect.to(saidaPadrao.toFile()));
        Process processo;
        try {
            processo = builder.start();
        } catch (IOException e) {
            throw new FalhaFfmpegException("Não foi possível executar " + args.getFirst() + ": " + e.getMessage());
        }
        long fim = System.nanoTime() + limite.toNanos();
        try {
            while (!processo.waitFor(Math.min(INTERVALO.toMillis(), restante(fim)), TimeUnit.MILLISECONDS)) {
                if (restante(fim) <= 0) {
                    processo.destroyForcibly();
                    throw new FalhaFfmpegException(args.getFirst() + " passou do tempo limite de " + limite + ".");
                }
                aCadaMinuto.run();
            }
            return processo.exitValue();
        } catch (InterruptedException e) {
            processo.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new FalhaFfmpegException(args.getFirst() + " foi interrompido.");
        }
    }

    private void exigirSucesso(int codigo, Path diretorio, String etapa) {
        if (codigo != 0) {
            throw new FalhaFfmpegException(
                    "Falha na %s (código %d): %s".formatted(etapa, codigo, fimDoErro(diretorio, null)));
        }
    }

    private static String fimDoErro(Path diretorio, String saidaPadrao) {
        Path arquivo = arquivoErro(diretorio, saidaPadrao == null ? null : diretorio.resolve(saidaPadrao));
        try {
            String texto = Files.readString(arquivo, StandardCharsets.UTF_8).strip();
            return texto.length() <= CARACTERES_ERRO ? texto : texto.substring(texto.length() - CARACTERES_ERRO);
        } catch (IOException e) {
            return "(sem saída de erro)";
        }
    }

    private static Path arquivoErro(Path diretorio, Path saidaPadrao) {
        return saidaPadrao == null
                ? diretorio.resolve(ARQUIVO_ERRO)
                : saidaPadrao.resolveSibling(saidaPadrao.getFileName() + ".err");
    }

    private static long restante(long fim) {
        return Math.max(0, TimeUnit.NANOSECONDS.toMillis(fim - System.nanoTime()));
    }

    private static void criarDiretorios(Path saida, List<Variante> variantes) {
        try {
            for (Variante v : variantes) {
                Files.createDirectories(saida.resolve(v.nome()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void apagar(Path arquivo) {
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
