package br.com.celke.cursos.video.infra;

import br.com.celke.cursos.video.domain.AcessoVideo;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AcessoVideoRepository extends JpaRepository<AcessoVideo, Long> {

    List<AcessoVideo> findByUsuarioIdAndAulaIdOrderByAcessadoEm(Long usuarioId, Long aulaId);

    /** Apaga os registros anteriores ao limite; devolve quantos saíram. */
    @Modifying
    @Query("delete from AcessoVideo a where a.acessadoEm < :limite")
    int apagarAnteriores(Instant limite);
}
