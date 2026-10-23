package br.com.celke.cursos.shared.config;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Logs em JSON (ECS) com o {@code @timestamp} no horário de Brasília, com o deslocamento explícito
 * ({@code 2026-10-22T10:33:20.123-03:00}): quem lê o log na VPS vê a hora local sem fazer conta, e o
 * valor continua sendo um instante ISO-8601 válido. Ligado em {@code logging.structured.json.customizer}.
 * Só a exibição muda: as regras de negócio e o banco continuam em instantes absolutos.
 */
public class FusoHorarioLogs implements StructuredLoggingJsonMembersCustomizer<Object> {

    public static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(BRASILIA);

    @Override
    public void customize(JsonWriter.Members<Object> members) {
        members.applyingValueProcessor(
                JsonWriter.ValueProcessor.of(FusoHorarioLogs::emBrasilia).whenHasPath("@timestamp"));
    }

    static Object emBrasilia(Object valor) {
        return valor instanceof Instant instante ? FORMATO.format(instante) : valor;
    }
}
