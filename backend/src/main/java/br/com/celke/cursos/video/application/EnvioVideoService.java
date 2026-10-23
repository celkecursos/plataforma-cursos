package br.com.celke.cursos.video.application;

import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.armazenamento.StorageService.ParteEnviada;
import br.com.celke.cursos.video.domain.DivisaoPartes;
import br.com.celke.cursos.video.domain.FormatoVideo;
import br.com.celke.cursos.video.domain.Video;
import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.core.exception.SdkException;

/**
 * Envio do vídeo da aula direto do navegador para o bucket, em partes, por URLs pré-assinadas: a API
 * nunca recebe o arquivo. Na conclusão, a API confere o tamanho e a assinatura reais do arquivo antes
 * de colocá-lo na fila de conversão.
 */
@Service
@EnableConfigurationProperties(VideoProperties.class)
public class EnvioVideoService {

    static final Duration VALIDADE_THUMBNAIL = Duration.ofHours(1);

    private final VideoAdmService videoAdmService;
    private final StorageService storage;
    private final VideoProperties props;
    private final Clock clock;

    public EnvioVideoService(
            VideoAdmService videoAdmService, StorageService storage, VideoProperties props, Clock clock) {
        this.videoAdmService = videoAdmService;
        this.storage = storage;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Valida tipo e tamanho antes de assinar, abre o upload em partes e devolve uma URL por parte. Um
     * envio anterior ainda aberto na mesma aula é abortado.
     */
    public EnvioIniciado iniciar(Long aulaId, String nomeArquivo, String contentType, long tamanho) {
        FormatoVideo formato = FormatoVideo.doContentType(contentType)
                .orElseThrow(() -> new VideoInvalidoException("Envie um vídeo MP4, MOV, MKV ou WebM."));
        if (tamanho > props.tamanhoMaximo().toBytes()) {
            throw new VideoInvalidoException(
                    "O vídeo pode ter no máximo %s.".formatted(legivel(props.tamanhoMaximo())));
        }
        // Confere a aula e o status antes de abrir um upload no bucket
        videoAdmService.exigirEnvioPermitido(aulaId);
        String chave = "uploads/videos/%d/%s".formatted(aulaId, UUID.randomUUID());
        String uploadId = storage.iniciarMultipart(chave, formato.contentType());
        VideoAdmService.EnvioRegistrado registro;
        try {
            registro = videoAdmService.registrarEnvio(aulaId, chave, uploadId, nomeArquivo.strip(), tamanho);
        } catch (RuntimeException e) {
            storage.abortarMultipart(chave, uploadId);
            throw e;
        }
        registro.anterior().ifPresent(a -> abortarSemFalhar(a.chave(), a.uploadId()));
        DivisaoPartes divisao = DivisaoPartes.de(tamanho);
        List<UrlParte> partes = IntStream.rangeClosed(1, divisao.quantidade())
                .mapToObj(n -> new UrlParte(n, storage.urlParte(chave, uploadId, n, props.validadeEnvio())))
                .toList();
        return new EnvioIniciado(
                registro.video(),
                divisao.tamanhoParte(),
                partes,
                clock.instant().plus(props.validadeEnvio()));
    }

    /**
     * Junta as partes e confere o arquivo real: tamanho igual ao informado e início com a assinatura do
     * formato. Arquivo recusado é apagado e o vídeo fica com erro. Aceito, vai para a fila.
     */
    public Video concluir(Long aulaId, List<ParteEnviada> partes) {
        Video video = videoAdmService.envioEmAndamento(aulaId);
        Set<Integer> numeros = partes.stream().map(ParteEnviada::numero).collect(Collectors.toSet());
        if (numeros.size() != partes.size()) {
            throw new VideoInvalidoException("Cada parte pode ser informada uma vez só.");
        }
        String chave = video.getUploadChave();
        try {
            storage.concluirMultipart(chave, video.getUploadId(), partes);
        } catch (SdkException e) {
            throw new VideoInvalidoException("O envio não foi concluído. Confira se todas as partes foram enviadas.");
        }
        var info = storage.info(chave)
                .orElseThrow(() -> new VideoInvalidoException("O vídeo não foi enviado. Comece um novo envio."));
        if (info.tamanho() != video.getTamanhoOriginal()) {
            recusar(video, chave, "O tamanho do arquivo enviado não confere com o informado. Comece um novo envio.");
        }
        Optional<FormatoVideo> formato = FormatoVideo.doContentType(info.contentType());
        if (formato.isEmpty()
                || !formato.get().assinaturaConfere(storage.baixarInicio(chave, FormatoVideo.BYTES_ASSINATURA))) {
            recusar(video, chave, "O arquivo não é um vídeo MP4, MOV, MKV ou WebM.");
        }
        return videoAdmService.enfileirar(video.getId(), info.tamanho());
    }

    /** Volta para a fila um vídeo com erro, se o original ainda está no bucket. */
    public Video reprocessar(Long aulaId) {
        Video video = videoAdmService.buscarPorAula(aulaId);
        if (video.getUploadChave() != null
                && storage.info(video.getUploadChave()).isEmpty()) {
            videoAdmService.descartarOriginal(video.getId());
            throw VideoAdmService.originalIndisponivel();
        }
        return videoAdmService.reprocessar(aulaId);
    }

    /** Remove o vídeo da aula e os arquivos dele no bucket (original, envio aberto, HLS e thumbnail). */
    public void excluir(Long aulaId) {
        Video video = videoAdmService.excluir(aulaId);
        if (video.getUploadId() != null) {
            abortarSemFalhar(video.getUploadChave(), video.getUploadId());
        } else if (video.getUploadChave() != null) {
            storage.excluir(video.getUploadChave());
        }
        if (video.getPlaylistChave() != null) {
            storage.excluirPrefixo(prefixoHls(video.getPlaylistChave()));
        }
    }

    /** URL temporária da thumbnail, ou vazio sem vídeo pronto. */
    public Optional<URL> urlThumbnail(Video video) {
        return Optional.ofNullable(video.getThumbnailChave())
                .map(chave -> storage.urlDownload(chave, VALIDADE_THUMBNAIL));
    }

    /** {@code videos/<aula>/<job>/master.m3u8} → {@code videos/<aula>/<job>/} */
    static String prefixoHls(String playlistChave) {
        return playlistChave.substring(0, playlistChave.lastIndexOf('/') + 1);
    }

    private void recusar(Video video, String chave, String motivo) {
        storage.excluir(chave);
        videoAdmService.recusarEnvio(video.getId(), motivo);
        throw new VideoInvalidoException(motivo);
    }

    private void abortarSemFalhar(String chave, String uploadId) {
        try {
            storage.abortarMultipart(chave, uploadId);
        } catch (SdkException e) {
            // Upload já concluído ou abortado: a regra de expiração do bucket limpa o que sobrar
        }
    }

    static String legivel(DataSize tamanho) {
        long gb = DataSize.ofGigabytes(1).toBytes();
        return tamanho.toBytes() % gb == 0 ? tamanho.toGigabytes() + " GB" : tamanho.toMegabytes() + " MB";
    }

    public record UrlParte(int numero, URL url) {}

    public record EnvioIniciado(Video video, long tamanhoParte, List<UrlParte> partes, Instant expiraEm) {}
}
