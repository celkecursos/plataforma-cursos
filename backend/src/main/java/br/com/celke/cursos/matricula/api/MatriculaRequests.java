package br.com.celke.cursos.matricula.api;

import br.com.celke.cursos.matricula.application.NovaMatricula;
import br.com.celke.cursos.matricula.domain.FormaPagamento;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Corpos das rotas de matrícula do ADM. Datas em ISO-8601 com fuso (ex.: {@code 2026-03-01T03:00:00Z}). */
public final class MatriculaRequests {

    private MatriculaRequests() {}

    /** Sem início, começa agora; sem fim, não vence. */
    public record Matricular(
            @NotBlank @Email @Size(max = 254) String email,
            Instant inicio,
            Instant fim,
            @NotNull FormaPagamento formaPagamento,
            @Size(max = 100) String transacao) {

        public NovaMatricula dados() {
            return new NovaMatricula(email, inicio, fim, formaPagamento, transacao);
        }
    }

    /** Sem fim, a matrícula passa a não vencer. */
    public record Periodo(@NotNull Instant inicio, Instant fim) {}
}
