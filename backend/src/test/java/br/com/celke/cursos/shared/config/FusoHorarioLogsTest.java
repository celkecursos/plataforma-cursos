package br.com.celke.cursos.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.json.JsonWriter;

class FusoHorarioLogsTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "2026-10-22T13:33:20Z,     2026-10-22T10:33:20.000-03:00",
        "2026-10-22T02:59:59.123Z, 2026-10-21T23:59:59.123-03:00",
        "2026-01-15T00:00:00Z,     2026-01-14T21:00:00.000-03:00",
    })
    @DisplayName("instante do log no horário de Brasília, com o deslocamento")
    void emBrasilia(Instant instante, String esperado) {
        assertThat(FusoHorarioLogs.emBrasilia(instante)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("só o @timestamp muda; outros campos e outros tipos ficam como estão")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void soOTimestamp() {
        record Evento(Instant em, String mensagem) {}
        JsonWriter<Evento> escritor = JsonWriter.of(membros -> {
            membros.add("@timestamp", Evento::em);
            membros.add("outro", Evento::em);
            membros.add("message", Evento::mensagem);
            new FusoHorarioLogs().customize((JsonWriter.Members) membros);
        });

        String json = escritor.writeToString(new Evento(Instant.parse("2026-10-22T13:33:20Z"), "ok"));

        assertThat(json)
                .isEqualTo("{\"@timestamp\":\"2026-10-22T10:33:20.000-03:00\","
                        + "\"outro\":\"2026-10-22T13:33:20Z\",\"message\":\"ok\"}");
        assertThat(FusoHorarioLogs.emBrasilia("texto")).isEqualTo("texto");
    }
}
