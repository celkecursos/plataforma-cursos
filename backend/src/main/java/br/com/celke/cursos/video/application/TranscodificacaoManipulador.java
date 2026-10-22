package br.com.celke.cursos.video.application;

import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.fila.ErroPermanenteException;
import br.com.celke.cursos.shared.fila.FilaService;
import br.com.celke.cursos.shared.fila.Job;
import br.com.celke.cursos.shared.fila.ManipuladorJob;
import br.com.celke.cursos.video.domain.ResultadoTranscodificacao;
import br.com.celke.cursos.video.domain.Variante;
import br.com.celke.cursos.video.domain.Video;
import br.com.celke.cursos.video.infra.AnaliseVideo;
import br.com.celke.cursos.video.infra.Ffmpeg;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Converte o original em HLS (roda no worker): baixa do bucket, analisa com o ffprobe, gera as
 * variantes e a thumbnail com o ffmpeg, envia tudo para {@code videos/<aula>/<job>/}, publica e só
 * então apaga o original e o HLS anterior. Rodar de novo o mesmo job (o worker caiu no meio) refaz o
 * prefixo do job do zero. Falha comum volta para a fila; a desistência deixa o vídeo com erro.
 */
@Component
public class TranscodificacaoManipulador implements ManipuladorJob {

    private static final Logger LOG = LoggerFactory.getLogger(TranscodificacaoManipulador.class);

    private final VideoAdmService videoAdmService;
    private final StorageService storage;
    private final Ffmpeg ffmpeg;
    private final FilaService fila;
    private final JsonMapper jsonMapper;
    private final VideoProperties props;
    private final Clock clock;

    public TranscodificacaoManipulador(
            VideoAdmService videoAdmService,
            StorageService storage,
            Ffmpeg ffmpeg,
            FilaService fila,
            JsonMapper jsonMapper,
            VideoProperties props,
            Clock clock) {
        this.videoAdmService = videoAdmService;
        this.storage = storage;
        this.ffmpeg = ffmpeg;
        this.fila = fila;
        this.jsonMapper = jsonMapper;
        this.props = props;
        this.clock = clock;
    }

    @Override
    public String tipo() {
        return VideoAdmService.TIPO_JOB;
    }

    @Override
    public void processar(Job job) throws IOException {
        PayloadTranscodificacao payload = payload(job);
        Optional<Video> iniciado = videoAdmService.iniciarProcessamento(payload);
        if (iniciado.isEmpty()) {
            LOG.info("Job {}: o vídeo {} mudou depois deste envio; nada a fazer", job.id(), payload.videoId());
            return;
        }
        Video video = iniciado.get();
        Files.createDirectories(props.diretorioTrabalho());
        Path trabalho = Files.createTempDirectory(props.diretorioTrabalho(), "video-" + video.getId() + "-");
        try {
            Path entrada = trabalho.resolve("original");
            try {
                storage.baixarParaArquivo(payload.uploadChave(), entrada);
            } catch (NoSuchKeyException e) {
                throw new ErroPermanenteException(
                        "O arquivo original não está mais disponível. Envie o vídeo de novo.");
            }
            Runnable renovar = () -> fila.renovar(job, clock.instant());
            renovar.run();

            AnaliseVideo analise = ffmpeg.analisar(entrada, trabalho);
            List<Variante> variantes = Variante.paraOrigem(analise.altura());
            Path saida = Files.createDirectory(trabalho.resolve("hls"));
            long inicio = System.nanoTime();
            ffmpeg.converter(entrada, saida, variantes, analise, renovar);
            long tempoMs = (System.nanoTime() - inicio) / 1_000_000;

            String prefixo = "videos/%d/%d/".formatted(video.getAulaId(), job.id());
            storage.excluirPrefixo(prefixo);
            long tamanhoFinal = enviar(saida, prefixo);
            renovar.run();

            var resultado = new ResultadoTranscodificacao(
                    prefixo + "master.m3u8",
                    prefixo + "thumbnail.jpg",
                    tamanhoFinal,
                    analise.duracaoSegundos(),
                    analise.largura(),
                    analise.altura(),
                    variantes.stream().map(Variante::nome).collect(Collectors.joining(",")),
                    tempoMs);
            Optional<String> anterior = videoAdmService.concluir(video.getId(), resultado);
            storage.excluir(payload.uploadChave());
            anterior.map(EnvioVideoService::prefixoHls)
                    .filter(p -> !p.equals(prefixo))
                    .ifPresent(storage::excluirPrefixo);
            LOG.info(
                    "Vídeo {} convertido: {} s de vídeo em {} ms, {} → {} bytes ({})",
                    video.getId(),
                    analise.duracaoSegundos(),
                    tempoMs,
                    video.getTamanhoOriginal(),
                    tamanhoFinal,
                    resultado.variantes());
        } finally {
            apagarDiretorio(trabalho);
        }
    }

    @Override
    public void desistir(Job job, String motivo) {
        videoAdmService.falharProcessamento(payload(job), motivo);
    }

    /** Envia os arquivos gerados; devolve a soma do HLS (playlists e segmentos), sem a thumbnail. */
    private long enviar(Path saida, String prefixo) throws IOException {
        long total = 0;
        try (Stream<Path> arquivos = Files.walk(saida)) {
            for (Path arquivo : arquivos.filter(Files::isRegularFile).toList()) {
                String relativo = saida.relativize(arquivo).toString().replace('\\', '/');
                String tipo = contentType(relativo);
                storage.gravarArquivo(prefixo + relativo, arquivo, tipo);
                if (!tipo.startsWith("image/")) {
                    total += Files.size(arquivo);
                }
            }
        }
        return total;
    }

    static String contentType(String arquivo) {
        if (arquivo.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }
        if (arquivo.endsWith(".ts")) {
            return "video/mp2t";
        }
        if (arquivo.endsWith(".jpg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    private PayloadTranscodificacao payload(Job job) {
        return jsonMapper.readValue(job.payload(), PayloadTranscodificacao.class);
    }

    private static void apagarDiretorio(Path diretorio) {
        try (Stream<Path> caminhos = Files.walk(diretorio)) {
            caminhos.sorted(Comparator.reverseOrder()).forEach(caminho -> {
                try {
                    Files.deleteIfExists(caminho);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException | UncheckedIOException e) {
            LOG.warn("Não foi possível apagar o diretório de trabalho {}: {}", diretorio, e.toString());
        }
    }
}
