package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.domain.AuditoriaLogin;
import br.com.celke.cursos.auth.domain.ResultadoLogin;
import br.com.celke.cursos.auth.infra.AuditoriaLoginRepository;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra cada tentativa de login numa transação própria: o registro fica gravado mesmo quando o
 * login falha e a transação dele é desfeita. Os registros são apagados depois da retenção (6 meses,
 * prazo do Marco Civil para os registros de acesso a aplicações).
 */
@Service
@EnableConfigurationProperties(AuditoriaLoginProperties.class)
public class AuditoriaLoginService {

    private static final Logger LOG = LoggerFactory.getLogger(AuditoriaLoginService.class);

    private final AuditoriaLoginRepository repository;
    private final AuditoriaLoginProperties props;
    private final Clock clock;

    public AuditoriaLoginService(AuditoriaLoginRepository repository, AuditoriaLoginProperties props, Clock clock) {
        this.repository = repository;
        this.props = props;
        this.clock = clock;
    }

    /** Apaga as tentativas mais antigas que a retenção; devolve quantas saíram. */
    @Transactional
    public int apagarVencidas() {
        Instant limite = clock.instant().minus(props.retencao());
        int apagadas = repository.apagarAnteriores(limite);
        LOG.info("Auditoria de login: {} registro(s) anteriores a {} apagados", apagadas, limite);
        return apagadas;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String email, Usuario usuario, ResultadoLogin resultado, OrigemRequisicao origem) {
        repository.save(new AuditoriaLogin(
                Usuario.normalizarEmail(email),
                usuario == null ? null : usuario.getId(),
                resultado,
                origem.ip(),
                origem.userAgent(),
                clock.instant()));
    }
}
