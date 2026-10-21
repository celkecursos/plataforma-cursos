package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.domain.AuditoriaLogin;
import br.com.celke.cursos.auth.domain.ResultadoLogin;
import br.com.celke.cursos.auth.infra.AuditoriaLoginRepository;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra cada tentativa de login numa transação própria: o registro fica gravado mesmo quando o
 * login falha e a transação dele é desfeita.
 */
@Service
public class AuditoriaLoginService {

    private final AuditoriaLoginRepository repository;
    private final Clock clock;

    public AuditoriaLoginService(AuditoriaLoginRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
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
