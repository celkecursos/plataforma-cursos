package br.com.celke.cursos.termo.infra;

import br.com.celke.cursos.termo.domain.AceiteTermo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AceiteTermoRepository extends JpaRepository<AceiteTermo, Long> {

    boolean existsByUsuarioIdAndTermoId(Long usuarioId, Long termoId);
}
