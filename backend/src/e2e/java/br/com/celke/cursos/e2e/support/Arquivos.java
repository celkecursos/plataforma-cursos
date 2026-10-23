package br.com.celke.cursos.e2e.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** Amostras usadas nas jornadas (em {@code src/test/resources/amostras}, as mesmas dos testes de integração). */
public final class Arquivos {

    private Arquivos() {}

    public static byte[] amostra(String nome) {
        try (InputStream entrada = Arquivos.class.getResourceAsStream("/amostras/" + nome)) {
            if (entrada == null) {
                throw new IllegalStateException("Amostra não encontrada: " + nome);
            }
            return entrada.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
