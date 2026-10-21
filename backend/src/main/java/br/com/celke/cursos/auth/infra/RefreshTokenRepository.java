package br.com.celke.cursos.auth.infra;

import br.com.celke.cursos.auth.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** Trava a linha: dois refresh simultâneos com o mesmo token não passam juntos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByHash(String hash);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revogadoEm = :agora WHERE t.familia = :familia AND t.revogadoEm IS NULL")
    int revogarFamilia(UUID familia, Instant agora);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revogadoEm = :agora WHERE t.usuarioId = :usuarioId AND t.revogadoEm IS NULL")
    int revogarTodasDoUsuario(Long usuarioId, Instant agora);
}
