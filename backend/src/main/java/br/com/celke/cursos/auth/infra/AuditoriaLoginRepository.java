package br.com.celke.cursos.auth.infra;

import br.com.celke.cursos.auth.domain.AuditoriaLogin;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaLoginRepository extends JpaRepository<AuditoriaLogin, Long> {}
