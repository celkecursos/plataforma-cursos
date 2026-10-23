package br.com.celke.cursos.auth.infra;

import br.com.celke.cursos.auth.domain.CodigoRecuperacao;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CodigoRecuperacaoRepository extends JpaRepository<CodigoRecuperacao, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CodigoRecuperacao c WHERE c.usuarioId = :usuarioId AND c.hash = :hash AND c.usadoEm IS NULL")
    Optional<CodigoRecuperacao> pendente(Long usuarioId, String hash);

    long countByUsuarioIdAndUsadoEmIsNull(Long usuarioId);

    @Modifying
    @Query("DELETE FROM CodigoRecuperacao c WHERE c.usuarioId = :usuarioId")
    int apagarDoUsuario(Long usuarioId);
}
