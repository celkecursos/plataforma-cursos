package br.com.celke.cursos.auth.infra;

import br.com.celke.cursos.auth.domain.TipoToken;
import br.com.celke.cursos.auth.domain.TokenUsoUnico;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TokenUsoUnicoRepository extends JpaRepository<TokenUsoUnico, Long> {

    /** Trava a linha: o mesmo link usado duas vezes ao mesmo tempo só vale uma. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TokenUsoUnico> findByHash(String hash);

    long countByUsuarioIdAndTipoAndCriadoEmAfter(Long usuarioId, TipoToken tipo, Instant desde);

    /** Invalida os links ainda não usados (quando um novo é enviado). */
    @Modifying
    @Query("""
            UPDATE TokenUsoUnico t SET t.usadoEm = :agora
             WHERE t.usuarioId = :usuarioId AND t.tipo = :tipo AND t.usadoEm IS NULL
            """)
    int invalidarPendentes(Long usuarioId, TipoToken tipo, Instant agora);
}
