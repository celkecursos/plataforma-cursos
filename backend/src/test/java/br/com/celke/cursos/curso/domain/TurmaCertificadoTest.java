package br.com.celke.cursos.curso.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TurmaCertificadoTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    private static Turma turma() {
        return new Turma(new Curso("Java", "java", "Resumo", null, AGORA), "Turma", AGORA);
    }

    @Test
    @DisplayName("turma nova exige todas as aulas e não tem carga horária")
    void padrao() {
        assertThat(turma().getPercentualConclusao()).isEqualTo(100);
        assertThat(turma().getCargaHorariaHoras()).isNull();
    }

    @Test
    @DisplayName("define o percentual mínimo e a carga horária; os limites 1 e 100 são aceitos")
    void define() {
        Turma turma = turma();

        turma.definirCertificado(1, 40, AGORA);
        assertThat(turma.getPercentualConclusao()).isEqualTo(1);
        assertThat(turma.getCargaHorariaHoras()).isEqualTo(40);

        turma.definirCertificado(100, null, AGORA);
        assertThat(turma.getPercentualConclusao()).isEqualTo(100);
        assertThat(turma.getCargaHorariaHoras()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101})
    @DisplayName("percentual fora de 1 a 100 é recusado")
    void percentualInvalido(int percentual) {
        assertThatThrownBy(() -> turma().definirCertificado(percentual, null, AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O percentual de conclusão vai de 1 a 100.");
    }

    @Test
    @DisplayName("carga horária zero é recusada")
    void cargaInvalida() {
        assertThatThrownBy(() -> turma().definirCertificado(80, 0, AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A carga horária precisa ser positiva.");
    }
}
