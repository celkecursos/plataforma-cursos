package br.com.celke.cursos.video.infra;

import br.com.celke.cursos.video.domain.Video;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface VideoRepository extends JpaRepository<Video, Long> {

    Optional<Video> findByAulaId(Long aulaId);

    /** Vídeo travado até o fim da transação: envio, fila e worker não mudam o status ao mesmo tempo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Video v where v.aulaId = :aulaId")
    Optional<Video> travarPorAula(Long aulaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Video v where v.id = :id")
    Optional<Video> travar(Long id);
}
