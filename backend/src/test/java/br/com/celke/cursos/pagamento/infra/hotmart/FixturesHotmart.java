package br.com.celke.cursos.pagamento.infra.hotmart;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Corpos do webhook 2.0.0 em {@code src/test/resources/pagamentos/hotmart} (também usados pelos ITs). */
public final class FixturesHotmart {

    /** Marcador do código do pedido no {@code xcod} das fixtures. */
    public static final String XCOD = "XCOD_DO_PEDIDO";

    private FixturesHotmart() {}

    public static String corpo(String evento) {
        try (InputStream entrada =
                FixturesHotmart.class.getResourceAsStream("/pagamentos/hotmart/" + evento + ".json")) {
            if (entrada == null) {
                throw new IllegalArgumentException("Fixture inexistente: " + evento);
            }
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
