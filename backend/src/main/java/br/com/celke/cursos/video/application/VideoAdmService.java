package br.com.celke.cursos.video.application;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.fila.FilaService;
import br.com.celke.cursos.video.domain.ResultadoTranscodificacao;
import br.com.celke.cursos.video.domain.StatusVideo;
import br.com.celke.cursos.video.domain.Video;
import br.com.celke.cursos.video.infra.VideoRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Estado do vídeo de cada aula no banco. Toda mudança de status trava a linha do vídeo, para o ADM e o
 * worker não se atropelarem. Não fala com o armazenamento: isso fica no {@link EnvioVideoService} e no
 * manipulador do job.
 */
@Service
public class VideoAdmService {

    public static final String TIPO_JOB = "video.transcodificar";

    private final VideoRepository videos;
    private final ConteudoTurmaService conteudoTurmaService;
    private final FilaService fila;
    private final Clock clock;

    public VideoAdmService(
            VideoRepository videos, ConteudoTurmaService conteudoTurmaService, FilaService fila, Clock clock) {
        this.videos = videos;
        this.conteudoTurmaService = conteudoTurmaService;
        this.fila = fila;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Video buscarPorAula(Long aulaId) {
        exigirAula(aulaId);
        return videos.findByAulaId(aulaId).orElseThrow(VideoAdmService::semVideo);
    }

    /** Aula existe e o vídeo dela (se houver) não está na fila nem com o worker. */
    @Transactional(readOnly = true)
    public void exigirEnvioPermitido(Long aulaId) {
        exigirAula(aulaId);
        videos.findByAulaId(aulaId).ifPresent(VideoAdmService::exigirForaDoProcessamento);
    }

    /**
     * Registra um novo envio na aula (cria o vídeo no primeiro). Devolve o envio anterior ainda não
     * concluído, cujo upload em partes deve ser abortado.
     */
    @Transactional
    public EnvioRegistrado registrarEnvio(Long aulaId, String chave, String uploadId, String nome, long tamanho) {
        exigirAula(aulaId);
        Instant agora = clock.instant();
        Video video = videos.travarPorAula(aulaId).orElseGet(() -> new Video(aulaId, agora));
        exigirForaDoProcessamento(video);
        Optional<EnvioAnterior> anterior =
                video.getStatus() == StatusVideo.AGUARDANDO_ENVIO && video.getUploadId() != null
                        ? Optional.of(new EnvioAnterior(video.getUploadChave(), video.getUploadId()))
                        : Optional.empty();
        video.iniciarEnvio(chave, uploadId, nome, tamanho, agora);
        try {
            // Dois primeiros envios ao mesmo tempo: a restrição única da aula recusa o segundo
            return new EnvioRegistrado(videos.saveAndFlush(video), anterior);
        } catch (DataIntegrityViolationException e) {
            throw new ConflitoException("Outro envio para esta aula começou ao mesmo tempo. Tente de novo.");
        }
    }

    /** Envio em andamento da aula (o navegador terminou de enviar as partes e pede a conclusão). */
    @Transactional(readOnly = true)
    public Video envioEmAndamento(Long aulaId) {
        return videos.findByAulaId(aulaId)
                .filter(v -> v.getStatus() == StatusVideo.AGUARDANDO_ENVIO && v.getUploadId() != null)
                .orElseThrow(() -> {
                    exigirAula(aulaId);
                    return new ConflitoException("Nenhum envio em andamento para esta aula. Comece um novo envio.");
                });
    }

    /** Arquivo conferido: grava o tamanho real e coloca o job na fila, na mesma transação. */
    @Transactional
    public Video enfileirar(Long videoId, long tamanhoReal) {
        Video video = travar(videoId);
        if (video.getStatus() != StatusVideo.AGUARDANDO_ENVIO) {
            throw new ConflitoException("Nenhum envio em andamento para esta aula. Comece um novo envio.");
        }
        Instant agora = clock.instant();
        video.enfileirar(tamanhoReal, agora);
        fila.enfileirar(TIPO_JOB, new PayloadTranscodificacao(video.getId(), video.getUploadChave()), agora);
        return video;
    }

    /** Envio recusado na conferência: o original já foi apagado. */
    @Transactional
    public void recusarEnvio(Long videoId, String motivo) {
        Video video = travar(videoId);
        video.falhar(motivo, clock.instant());
        video.descartarOriginal(clock.instant());
    }

    /** Vídeo com erro volta para a fila a partir do original. */
    @Transactional
    public Video reprocessar(Long aulaId) {
        exigirAula(aulaId);
        Video video = videos.travarPorAula(aulaId).orElseThrow(VideoAdmService::semVideo);
        if (video.getStatus() != StatusVideo.ERRO) {
            throw new ConflitoException("Só um vídeo com erro pode ser reprocessado.");
        }
        if (video.getUploadChave() == null) {
            throw originalIndisponivel();
        }
        Instant agora = clock.instant();
        video.reprocessar(agora);
        fila.enfileirar(TIPO_JOB, new PayloadTranscodificacao(video.getId(), video.getUploadChave()), agora);
        return video;
    }

    /** O original do vídeo com erro sumiu do bucket (expirou): só um envio novo resolve. */
    @Transactional
    public void descartarOriginal(Long videoId) {
        travar(videoId).descartarOriginal(clock.instant());
    }

    /** Remove o vídeo da aula; devolve o que precisa ser apagado do bucket. */
    @Transactional
    public Video excluir(Long aulaId) {
        exigirAula(aulaId);
        Video video = videos.travarPorAula(aulaId).orElseThrow(VideoAdmService::semVideo);
        exigirForaDoProcessamento(video);
        videos.delete(video);
        return video;
    }

    // ---- Worker ----

    /**
     * Começa a processar o envio do job; vazio se o job ficou velho (vídeo excluído, outro envio feito
     * depois ou status que não é de fila).
     */
    @Transactional
    public Optional<Video> iniciarProcessamento(PayloadTranscodificacao payload) {
        return videos.travar(payload.videoId())
                .filter(v ->
                        v.getStatus().emProcessamento() && payload.uploadChave().equals(v.getUploadChave()))
                .map(v -> {
                    v.iniciarProcessamento(clock.instant());
                    return v;
                });
    }

    /** Publica o resultado; devolve a playlist anterior (a apagar), se havia. */
    @Transactional
    public Optional<String> concluir(Long videoId, ResultadoTranscodificacao resultado) {
        return Optional.ofNullable(travar(videoId).concluir(resultado, clock.instant()));
    }

    /** Falha definitiva do job; ignora o vídeo que já recebeu outro envio ou foi excluído. */
    @Transactional
    public void falharProcessamento(PayloadTranscodificacao payload, String motivo) {
        videos.travar(payload.videoId())
                .filter(v ->
                        v.getStatus().emProcessamento() && payload.uploadChave().equals(v.getUploadChave()))
                .ifPresent(v -> v.falhar(motivo, clock.instant()));
    }

    private Video travar(Long videoId) {
        return videos.travar(videoId).orElseThrow(VideoAdmService::semVideo);
    }

    private void exigirAula(Long aulaId) {
        if (!conteudoTurmaService.aulaExiste(aulaId)) {
            throw new NaoEncontradoException("Aula não encontrada.");
        }
    }

    private static void exigirForaDoProcessamento(Video video) {
        if (video.getStatus().emProcessamento()) {
            throw emProcessamento();
        }
    }

    static NaoEncontradoException semVideo() {
        return new NaoEncontradoException("A aula não tem vídeo.");
    }

    static ConflitoException emProcessamento() {
        return new ConflitoException("O vídeo da aula está em processamento. Aguarde o fim para alterá-lo.");
    }

    static ConflitoException originalIndisponivel() {
        return new ConflitoException("O arquivo original não está mais disponível. Envie o vídeo de novo.");
    }

    /** Envio anterior ainda não concluído (upload em partes aberto no bucket). */
    public record EnvioAnterior(String chave, String uploadId) {}

    public record EnvioRegistrado(Video video, Optional<EnvioAnterior> anterior) {}
}
