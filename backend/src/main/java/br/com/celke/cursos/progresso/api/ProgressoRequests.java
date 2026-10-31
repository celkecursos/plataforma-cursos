package br.com.celke.cursos.progresso.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Corpos das rotas de progresso do aluno. */
public final class ProgressoRequests {

    /** Maior posição aceita: 24 horas de vídeo. */
    public static final int POSICAO_MAXIMA = 86_400;

    private ProgressoRequests() {}

    /**
     * Só os campos enviados mudam: o player manda a posição de tempos em tempos; a tela da aula, vista e
     * revisar.
     */
    public record Atualizar(@Min(0) @Max(POSICAO_MAXIMA) Integer posicaoSegundos, Boolean vista, Boolean revisar) {

        @JsonIgnore
        @Schema(hidden = true)
        @AssertTrue(message = "informe a posição, vista ou revisar")
        public boolean isAlgumCampo() {
            return posicaoSegundos != null || vista != null || revisar != null;
        }
    }
}
