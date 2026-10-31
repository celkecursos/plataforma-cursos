package br.com.celke.cursos.forum.infra;

import br.com.celke.cursos.forum.domain.Topico;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TopicoRepository extends JpaRepository<Topico, Long> {

    /** Tópicos visíveis da turma (todos ou só os de uma aula): fixados primeiro, depois os mais recentes. */
    @Query("""
            select t from Topico t
             where t.turmaId = :turmaId and t.oculto = false and (:aulaId is null or t.aulaId = :aulaId)
             order by t.fixado desc, t.criadoEm desc, t.id desc
            """)
    Page<Topico> visiveis(Long turmaId, Long aulaId, Pageable pagina);

    /** Todos os tópicos da turma, ocultos inclusive (moderação). */
    @Query("""
            select t from Topico t where t.turmaId = :turmaId
             order by t.fixado desc, t.criadoEm desc, t.id desc
            """)
    Page<Topico> daTurma(Long turmaId, Pageable pagina);

    Optional<Topico> findByIdAndOcultoFalse(Long id);

    long countByAutorIdAndCriadoEmAfter(Long autorId, Instant desde);

    Optional<Topico> findFirstByAutorIdAndCriadoEmAfterOrderByCriadoEm(Long autorId, Instant desde);
}
