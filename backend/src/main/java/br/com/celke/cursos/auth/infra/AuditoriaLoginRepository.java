package br.com.celke.cursos.auth.infra;

import br.com.celke.cursos.auth.domain.AuditoriaLogin;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AuditoriaLoginRepository extends JpaRepository<AuditoriaLogin, Long> {

    @Modifying
    @Query("DELETE FROM AuditoriaLogin a WHERE a.criadoEm < :limite")
    int apagarAnteriores(Instant limite);
}
