package br.com.celke.cursos.e2e.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Notificações da Hotmart montadas a partir das fixtures do webhook 2.0.0
 * ({@code src/test/resources/pagamentos/hotmart}), com id, pedido, transação e produto da jornada.
 */
public final class Hotmart {

    private Hotmart() {}

    public static String notificacao(String evento, String xcod, String transacao, String produto, long ocorridoEm) {
        return fixture(evento)
                .replaceFirst("\"id\": \"[^\"]+\"", "\"id\": \"" + UUID.randomUUID() + "\"")
                .replaceFirst("\"creation_date\": \\d+", "\"creation_date\": " + ocorridoEm)
                .replace("XCOD_DO_PEDIDO", xcod)
                .replace("HP17715690036014", transacao)
                .replace("\"id\": 4774438", "\"id\": \"" + produto + "\"")
                .replace("\"code\": \"k2pasun0\"", "\"code\": \"e2e\"");
    }

    private static String fixture(String evento) {
        try (InputStream entrada = Hotmart.class.getResourceAsStream("/pagamentos/hotmart/" + evento + ".json")) {
            if (entrada == null) {
                throw new IllegalArgumentException("Fixture inexistente: " + evento);
            }
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
