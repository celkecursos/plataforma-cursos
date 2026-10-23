package br.com.celke.cursos.forum.infra;

import br.com.celke.cursos.forum.domain.Anexo;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnexoRepository extends JpaRepository<Anexo, Long> {

    List<Anexo> findByTopicoIdOrderById(Long topicoId);

    List<Anexo> findByRespostaIdInOrderById(Collection<Long> respostaIds);
}
