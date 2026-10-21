package br.com.celke.cursos.shared.fila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.support.fixtures.RelogioAjustavel;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BatimentoWorkerTest {

    private static final Instant AGORA = Instant.parse("2026-01-10T12:00:00Z");

    @TempDir
    Path pasta;

    @Test
    @DisplayName("cria o arquivo no primeiro batimento e atualiza a data a cada batimento")
    void bate() throws Exception {
        Path arquivo = pasta.resolve("batimento");
        RelogioAjustavel relogio = new RelogioAjustavel(AGORA);
        BatimentoWorker batimento = new BatimentoWorker(arquivo, relogio);

        batimento.bater();
        assertThat(Files.getLastModifiedTime(arquivo).toInstant()).isEqualTo(AGORA);

        relogio.avancar(Duration.ofSeconds(10));
        batimento.bater();
        assertThat(Files.getLastModifiedTime(arquivo).toInstant()).isEqualTo(AGORA.plusSeconds(10));
    }

    @Test
    @DisplayName("falha de escrita aparece como erro, em vez de passar em silêncio")
    void falhaDeEscrita() {
        Path dentroDeArquivoInexistente = pasta.resolve("nao-existe").resolve("batimento");
        BatimentoWorker batimento = new BatimentoWorker(dentroDeArquivoInexistente, new RelogioAjustavel(AGORA));

        assertThatThrownBy(batimento::bater).isInstanceOf(UncheckedIOException.class);
    }
}
