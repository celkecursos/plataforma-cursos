package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.auth.application.AuditoriaLoginService;
import br.com.celke.cursos.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Retenção da auditoria de login: o que passou de 180 dias sai; o resto fica. */
class AuditoriaLoginRetencaoIT extends IntegrationTest {

    @Autowired
    private AuditoriaLoginService auditoriaLoginService;

    @Autowired
    private JdbcClient jdbc;

    private void registrar(String email, Instant quando) {
        jdbc.sql("""
                        INSERT INTO auditoria_login (email, resultado, ip, criado_em)
                        VALUES (?, 'SENHA_INVALIDA', '203.0.113.7', ?)
                        """).param(email).param(Timestamp.from(quando)).update();
    }

    private long quantos(String email) {
        return jdbc.sql("SELECT count(*) FROM auditoria_login WHERE email = ?")
                .param(email)
                .query(Long.class)
                .single();
    }

    @Test
    @DisplayName("apaga as tentativas de mais de 180 dias e mantém as recentes")
    void retencao() {
        String antiga = stamp() + "@teste.celke.uno";
        String recente = stamp() + "@teste.celke.uno";
        registrar(antiga, Instant.now().minus(Duration.ofDays(181)));
        registrar(recente, Instant.now().minus(Duration.ofDays(179)));

        assertThat(auditoriaLoginService.apagarVencidas()).isPositive();

        assertThat(quantos(antiga)).isZero();
        assertThat(quantos(recente)).isEqualTo(1);
    }
}
