package br.com.celke.cursos.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Containers dos testes de integração. Como todas as classes de teste compartilham o mesmo
 * contexto Spring, cada container sobe uma vez por execução.
 */
@TestConfiguration(proxyBeanMethods = false)
public class Containers {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18-alpine");
    }
}
