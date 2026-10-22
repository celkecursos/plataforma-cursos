package br.com.celke.cursos.progresso.infra;

import br.com.celke.cursos.progresso.domain.ProgressoAula;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgressoAulaRepository extends JpaRepository<ProgressoAula, Long> {

    Optional<ProgressoAula> findByUsuarioIdAndAulaId(Long usuarioId, Long aulaId);

    /** Progresso do aluno em várias aulas numa consulta só (turma). */
    List<ProgressoAula> findByUsuarioIdAndAulaIdIn(Long usuarioId, Collection<Long> aulaIds);
}
