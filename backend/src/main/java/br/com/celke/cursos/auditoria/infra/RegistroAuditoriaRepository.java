package br.com.celke.cursos.auditoria.infra;

import br.com.celke.cursos.auditoria.domain.RegistroAuditoria;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface RegistroAuditoriaRepository
        extends JpaRepository<RegistroAuditoria, Long>, JpaSpecificationExecutor<RegistroAuditoria> {

    /** Entidades que já aparecem na trilha (filtro da tela). */
    @Query("select distinct r.entidade from RegistroAuditoria r order by r.entidade")
    List<String> entidades();
}
