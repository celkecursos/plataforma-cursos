package br.com.celke.cursos.shared.fila;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Consumo contínuo da fila. Só ligado no worker ({@code app.fila.consumidor-ativo=true}). */
@Configuration
@EnableScheduling
@ConditionalOnBooleanProperty("app.fila.consumidor-ativo")
public class ConsumidorFila {

    private final ProcessadorFila processador;

    public ConsumidorFila(ProcessadorFila processador) {
        this.processador = processador;
    }

    /** Esvazia a fila e espera o intervalo antes de olhar de novo. */
    @Scheduled(fixedDelayString = "${app.fila.intervalo}")
    void consumir() {
        while (processador.processarProximo()) {
            // continua enquanto houver job disponível
        }
    }
}
