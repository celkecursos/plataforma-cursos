package br.com.celke.cursos.forum.infra;

import br.com.celke.cursos.forum.domain.Resposta;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RespostaRepository extends JpaRepository<Resposta, Long> {

    List<Resposta> findByTopicoIdOrderByCriadoEmAscIdAsc(Long topicoId);

    List<Resposta> findByTopicoIdAndOcultoFalseOrderByCriadoEmAscIdAsc(Long topicoId);

    /** Respostas visíveis por tópico, numa consulta só (lista de tópicos). */
    @Query("""
            select r.topicoId, count(r) from Resposta r
             where r.topicoId in :topicos and r.oculto = false
             group by r.topicoId
            """)
    List<Object[]> contarVisiveis(Collection<Long> topicos);

    long countByAutorIdAndCriadoEmAfter(Long autorId, Instant desde);

    Optional<Resposta> findFirstByAutorIdAndCriadoEmAfterOrderByCriadoEm(Long autorId, Instant desde);
}
