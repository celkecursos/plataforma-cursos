package br.com.celke.cursos.auditoria.infra;

import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Apaga a trilha antiga. O banco só aceita a exclusão com {@code app.auditoria_retencao} ligado na
 * transação: nenhuma outra parte da aplicação consegue apagar registros.
 */
@Repository
public class RetencaoAuditoriaJdbc {

    private final JdbcClient jdbc;

    public RetencaoAuditoriaJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public int apagarAnteriores(Instant limite) {
        jdbc.sql("SELECT set_config('app.auditoria_retencao', 'on', true)")
                .query()
                .singleValue();
        return jdbc.sql("DELETE FROM auditoria WHERE em < :limite")
                .param("limite", limite.atOffset(ZoneOffset.UTC))
                .update();
    }
}
