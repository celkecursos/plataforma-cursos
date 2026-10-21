package br.com.celke.cursos.shared.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Relógio único da aplicação: regras de negócio recebem o {@link Clock}, nunca chamam {@code now()}. */
@Configuration
public class RelogioConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
