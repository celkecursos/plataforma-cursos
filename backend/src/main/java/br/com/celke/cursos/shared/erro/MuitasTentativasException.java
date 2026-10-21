package br.com.celke.cursos.shared.erro;

import java.time.Duration;
import org.springframework.http.HttpStatus;

/** Limite de tentativas atingido (429). A espera vai no cabeçalho {@code Retry-After}. */
public class MuitasTentativasException extends RegraNegocioException {

    private final Duration espera;

    public MuitasTentativasException(Duration espera) {
        super(HttpStatus.TOO_MANY_REQUESTS, mensagem(espera));
        this.espera = espera;
    }

    public Duration getEspera() {
        return espera;
    }

    /** Segundos para o {@code Retry-After}, arredondados para cima e nunca menos de 1. */
    public long segundosDeEspera() {
        return Math.max(1, (espera.toMillis() + 999) / 1000);
    }

    /** Minutos arredondados para cima: "1 minuto", "15 minutos". */
    public static String mensagem(Duration espera) {
        long minutos = Math.max(1, (espera.toSeconds() + 59) / 60);
        return "Muitas tentativas. Tente novamente em " + minutos + (minutos == 1 ? " minuto." : " minutos.");
    }
}
