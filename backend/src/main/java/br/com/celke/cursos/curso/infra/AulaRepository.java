package br.com.celke.cursos.curso.infra;

import br.com.celke.cursos.curso.domain.Aula;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AulaRepository extends JpaRepository<Aula, Long> {

    List<Aula> findByModuloIdOrderByOrdem(Long moduloId);

    /** Aulas de vários módulos numa consulta só (catálogo). */
    List<Aula> findByModuloIdInAndStatusOrderByOrdem(Collection<Long> moduloIds, StatusConteudo status);

    boolean existsByModuloId(Long moduloId);
}
