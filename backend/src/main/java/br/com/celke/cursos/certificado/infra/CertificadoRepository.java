package br.com.celke.cursos.certificado.infra;

import br.com.celke.cursos.certificado.domain.Certificado;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificadoRepository extends JpaRepository<Certificado, Long> {

    Optional<Certificado> findByUsuarioIdAndTurmaId(Long usuarioId, Long turmaId);

    Optional<Certificado> findByCodigo(String codigo);

    Optional<Certificado> findByIdAndUsuarioId(Long id, Long usuarioId);

    List<Certificado> findByUsuarioIdOrderByEmitidoEmDesc(Long usuarioId);
}
