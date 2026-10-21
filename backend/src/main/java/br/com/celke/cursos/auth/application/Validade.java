package br.com.celke.cursos.auth.application;

import java.time.Duration;

/** Validade de um link escrita para o e-mail: "1 hora", "48 horas", "30 minutos". */
final class Validade {

    private Validade() {}

    static String texto(Duration validade) {
        long minutos = validade.toMinutes();
        if (minutos >= 60 && minutos % 60 == 0) {
            long horas = minutos / 60;
            return horas + (horas == 1 ? " hora" : " horas");
        }
        return minutos + (minutos == 1 ? " minuto" : " minutos");
    }
}
