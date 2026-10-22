package br.com.celke.cursos.arquivo.infra;

import br.com.celke.cursos.arquivo.domain.ArquivoApoio;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArquivoApoioRepository extends JpaRepository<ArquivoApoio, Long> {

    List<ArquivoApoio> findByAulaIdOrderById(Long aulaId);

    List<ArquivoApoio> findByModuloIdOrderById(Long moduloId);
}
