package br.com.celke.cursos.shared.fila;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sinal de vida do worker, que não tem servidor HTTP para health check: regrava um arquivo a cada
 * 10 segundos e o healthcheck do Docker confere se ele mudou no último minuto. Roda separado do
 * consumo da fila, para um job demorado (como uma transcodificação) não parecer travamento.
 */
@Component
@ConditionalOnBooleanProperty("app.fila.consumidor-ativo")
public class BatimentoWorker {

    private final Path arquivo;
    private final Clock clock;

    public BatimentoWorker(@Value("${app.fila.arquivo-batimento}") Path arquivo, Clock clock) {
        this.arquivo = arquivo;
        this.clock = clock;
    }

    @Scheduled(fixedRate = 10_000)
    public void bater() {
        try {
            if (Files.notExists(arquivo)) {
                Files.createFile(arquivo);
            }
            Files.setLastModifiedTime(arquivo, FileTime.from(clock.instant()));
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível gravar o batimento em " + arquivo, e);
        }
    }
}
