package br.com.celke.cursos.curso.infra;

import br.com.celke.cursos.curso.domain.Aula;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AulaRepository extends JpaRepository<Aula, Long> {

    List<Aula> findByModuloIdOrderByOrdem(Long moduloId);

    /** Aulas de vários módulos numa consulta só (catálogo). */
    List<Aula> findByModuloIdInAndStatusOrderByOrdem(Collection<Long> moduloIds, StatusConteudo status);

    boolean existsByModuloId(Long moduloId);

    /** Aula com módulo, turma e curso carregados na mesma consulta (política de acesso). */
    @Query("select a from Aula a join fetch a.modulo m join fetch m.turma t join fetch t.curso where a.id = :id")
    Optional<Aula> comHierarquia(Long id);
}
