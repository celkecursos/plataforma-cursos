package br.com.celke.cursos.progresso.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProgressoAulaTest {

    private static final Instant T0 = Instant.parse("2026-03-10T12:00:00Z");
    private static final Instant T1 = T0.plusSeconds(600);

    @Test
    @DisplayName("progresso novo começa zerado")
    void novo() {
        ProgressoAula progresso = new ProgressoAula(7L, 9L, T0);

        assertThat(progresso.getPosicaoSegundos()).isZero();
        assertThat(progresso.isVista()).isFalse();
        assertThat(progresso.isRevisar()).isFalse();
        assertThat(progresso.getVistaEm()).isNull();
    }

    @Test
    @DisplayName("só os campos preenchidos mudam")
    void parcial() {
        ProgressoAula progresso = new ProgressoAula(7L, 9L, T0);
        progresso.atualizar(120, true, true, T0);

        progresso.atualizar(300, null, null, T1);

        assertThat(progresso.getPosicaoSegundos()).isEqualTo(300);
        assertThat(progresso.isVista()).isTrue();
        assertThat(progresso.isRevisar()).isTrue();
        assertThat(progresso.getVistaEm()).isEqualTo(T0);
        assertThat(progresso.getAtualizadoEm()).isEqualTo(T1);
    }

    @Test
    @DisplayName("marcar como vista guarda quando; marcar de novo mantém a data; desmarcar apaga")
    void vista() {
        ProgressoAula progresso = new ProgressoAula(7L, 9L, T0);

        progresso.atualizar(null, true, null, T0);
        assertThat(progresso.getVistaEm()).isEqualTo(T0);

        progresso.atualizar(null, true, null, T1);
        assertThat(progresso.getVistaEm()).isEqualTo(T0);

        progresso.atualizar(null, false, false, T1);
        assertThat(progresso.isVista()).isFalse();
        assertThat(progresso.getVistaEm()).isNull();
        assertThat(progresso.isRevisar()).isFalse();
    }

    @Test
    @DisplayName("posição zero é aceita; negativa é recusada sem mudar nada")
    void posicao() {
        ProgressoAula progresso = new ProgressoAula(7L, 9L, T0);
        progresso.atualizar(50, null, null, T0);

        progresso.atualizar(0, null, null, T0);
        assertThat(progresso.getPosicaoSegundos()).isZero();

        assertThatThrownBy(() -> progresso.atualizar(-1, true, null, T1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A posição não pode ser negativa.");
        assertThat(progresso.isVista()).isFalse();
    }
}
