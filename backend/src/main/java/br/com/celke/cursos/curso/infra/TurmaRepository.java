package br.com.celke.cursos.curso.infra;

import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.curso.domain.Turma;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface TurmaRepository extends JpaRepository<Turma, Long> {

    List<Turma> findByCursoIdOrderById(Long cursoId);

    boolean existsByCursoId(Long cursoId);

    /** Turma ativa mais recente: é o conteúdo que o catálogo mostra. */
    Optional<Turma> findFirstByCursoIdAndStatusOrderByIdDesc(Long cursoId, StatusTurma status);

    /** Trava a turma enquanto a ordem dos módulos muda (criar, reordenar, excluir). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Turma t where t.id = :id")
    Optional<Turma> travar(Long id);
}
