package br.com.celke.cursos.video.application;

import br.com.celke.cursos.matricula.application.AcessoAulaService;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.video.domain.ReescritaPlaylist;
import br.com.celke.cursos.video.domain.Video;
import br.com.celke.cursos.video.infra.VideoRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Entrega do vídeo ao aluno sem abrir o bucket: a API confere o acesso à aula ({@link AcessoAulaService})
 * a cada playlist e devolve os segmentos por URLs pré-assinadas, válidas pela duração do vídeo mais
 * uma margem. Substitui os cookies assinados de CDN, que esta hospedagem não tem.
 */
@Service
public class PlaylistService {

    private final AcessoAulaService acessoAulaService;
    private final VideoRepository videos;
    private final StorageService storage;
    private final LogAcessoVideoService logAcessoVideoService;
    private final VideoProperties props;

    public PlaylistService(
            AcessoAulaService acessoAulaService,
            VideoRepository videos,
            StorageService storage,
            LogAcessoVideoService logAcessoVideoService,
            VideoProperties props) {
        this.acessoAulaService = acessoAulaService;
        this.videos = videos;
        this.storage = storage;
        this.logAcessoVideoService = logAcessoVideoService;
        this.props = props;
    }

    /** Master playlist com as variantes servidas pela API; registra o acesso no log. */
    public String master(Long usuarioId, Long aulaId, OrigemRequisicao origem) {
        Video video = publicado(usuarioId, aulaId);
        String master = ReescritaPlaylist.master(texto(video.getPlaylistChave()), variantes(video));
        logAcessoVideoService.registrar(usuarioId, aulaId, origem);
        return master;
    }

    /** Playlist de uma variante com cada segmento por URL pré-assinada. */
    public String variante(Long usuarioId, Long aulaId, String variante) {
        Video video = publicado(usuarioId, aulaId);
        if (!variantes(video).contains(variante)) {
            throw new NaoEncontradoException("Variante não encontrada.");
        }
        String pasta = prefixo(video) + variante + "/";
        Duration validade = validade(video);
        return ReescritaPlaylist.variante(
                texto(pasta + "index.m3u8"),
                segmento -> storage.urlDownload(pasta + segmento, validade).toString());
    }

    /** Duração do vídeo mais a margem: dá para assistir inteiro, com pausas, sem a URL vencer. */
    Duration validade(Video video) {
        int duracao = video.getDuracaoSegundos() == null ? 0 : video.getDuracaoSegundos();
        return Duration.ofSeconds(duracao).plus(props.margemPlaylist());
    }

    /** Acesso à aula primeiro (404/403 da política), depois o vídeo publicado (404 sem vídeo). */
    private Video publicado(Long usuarioId, Long aulaId) {
        acessoAulaService.exigirAcesso(usuarioId, aulaId);
        return videos.findByAulaId(aulaId)
                .filter(v -> v.getPlaylistChave() != null)
                .orElseThrow(VideoAdmService::semVideo);
    }

    private String texto(String chave) {
        return new String(storage.baixar(chave), StandardCharsets.UTF_8);
    }

    private static Set<String> variantes(Video video) {
        return Set.of(video.getVariantes().split(","));
    }

    private static String prefixo(Video video) {
        return EnvioVideoService.prefixoHls(video.getPlaylistChave());
    }
}
