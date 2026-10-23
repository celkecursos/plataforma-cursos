package br.com.celke.cursos.curso.infra;

import br.com.celke.cursos.curso.domain.Modulo;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ModuloRepository extends JpaRepository<Modulo, Long> {

    List<Modulo> findByTurmaIdOrderByOrdem(Long turmaId);

    List<Modulo> findByTurmaIdAndStatusOrderByOrdem(Long turmaId, StatusConteudo status);

    boolean existsByTurmaId(Long turmaId);

    /** Trava o módulo enquanto a ordem das aulas muda (criar, reordenar, excluir). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Modulo m where m.id = :id")
    Optional<Modulo> travar(Long id);
}
