package br.com.celke.cursos.auth.infra;

import br.com.celke.cursos.auth.domain.SegundoFator;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SegundoFatorRepository extends JpaRepository<SegundoFator, Long> {

    /** Trava a linha: dois logins com o mesmo código ao mesmo tempo, só um passa. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SegundoFator s WHERE s.usuarioId = :usuarioId")
    Optional<SegundoFator> travar(Long usuarioId);
}
