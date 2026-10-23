package br.com.celke.cursos.video.application;

import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.video.domain.AcessoVideo;
import br.com.celke.cursos.video.infra.AcessoVideoRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Log de acesso aos vídeos: cada abertura da playlist de uma aula fica registrada com o IP e o
 * navegador, e sai do banco quando passa da retenção configurada (LGPD: o dado não fica para sempre).
 */
@Service
public class LogAcessoVideoService {

    private static final Logger LOG = LoggerFactory.getLogger(LogAcessoVideoService.class);

    private final AcessoVideoRepository acessos;
    private final VideoProperties props;
    private final Clock clock;

    public LogAcessoVideoService(AcessoVideoRepository acessos, VideoProperties props, Clock clock) {
        this.acessos = acessos;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public void registrar(Long usuarioId, Long aulaId, OrigemRequisicao origem) {
        acessos.save(new AcessoVideo(usuarioId, aulaId, origem.ip(), origem.userAgent(), clock.instant()));
    }

    /** Apaga o que passou da retenção; devolve quantos registros saíram. */
    @Transactional
    public int apagarVencidos() {
        Instant limite = clock.instant().minus(props.retencaoLogAcesso());
        int apagados = acessos.apagarAnteriores(limite);
        LOG.info("Log de acesso aos vídeos: {} registro(s) anteriores a {} apagados", apagados, limite);
        return apagados;
    }
}
