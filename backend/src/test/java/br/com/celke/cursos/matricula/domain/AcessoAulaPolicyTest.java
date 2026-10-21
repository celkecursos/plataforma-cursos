package br.com.celke.cursos.matricula.domain;

import static br.com.celke.cursos.support.fixtures.MatriculaFixture.cancelada;
import static br.com.celke.cursos.support.fixtures.MatriculaFixture.matricula;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AcessoAulaPolicyTest {

    private static final Instant INICIO = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant FIM = Instant.parse("2026-06-01T10:00:00Z");

    private static AcessoAulaPolicy em(Instant agora) {
        return new AcessoAulaPolicy(Clock.fixed(agora, ZoneOffset.UTC));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "um segundo antes do início,     2026-03-01T09:59:59Z, false, false",
        "exatamente no início,           2026-03-01T10:00:00Z, false, true",
        "exatamente no fim,              2026-06-01T10:00:00Z, false, true",
        "um segundo depois do fim,       2026-06-01T10:00:01Z, false, false",
        "antes do início — aula aberta,   2026-03-01T09:59:59Z, true,  true",
        "depois do fim — aula aberta,     2026-06-01T10:00:01Z, true,  true",
    })
    void acessoPeloPeriodoDaMatricula(String caso, Instant agora, boolean aulaAberta, boolean esperado) {
        assertThat(em(agora).podeAcessarAula(matricula(1, 2, 3, INICIO, FIM), aulaAberta))
                .isEqualTo(esperado);
    }

    @ParameterizedTest(name = "aula aberta: {0}")
    @CsvSource({"false, false", "true, true"})
    void semMatricula(boolean aulaAberta, boolean esperado) {
        assertThat(em(INICIO).podeAcessarAula(null, aulaAberta)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "aula aberta: {0}")
    @CsvSource({"false, false", "true, true"})
    void matriculaCanceladaDentroDoPeriodo(boolean aulaAberta, boolean esperado) {
        assertThat(em(INICIO.plusSeconds(60)).podeAcessarAula(cancelada(matricula(1, 2, 3, INICIO, FIM)), aulaAberta))
                .isEqualTo(esperado);
    }

    @Test
    @DisplayName("turma inteira só com matrícula vigente: aula aberta não abre a turma")
    void turmaSoComMatriculaVigente() {
        Instant dentro = Instant.parse("2026-04-15T00:00:00Z");
        assertThat(em(dentro).podeAcessarTurma(matricula(1, 2, 3, INICIO, FIM))).isTrue();
        assertThat(em(dentro).podeAcessarTurma(matricula(1, 2, 3, INICIO, null)))
                .isTrue();
        assertThat(em(dentro).podeAcessarTurma(null)).isFalse();
        assertThat(em(dentro).podeAcessarTurma(cancelada(matricula(1, 2, 3, INICIO, FIM))))
                .isFalse();
        assertThat(em(FIM.plusSeconds(1)).podeAcessarTurma(matricula(1, 2, 3, INICIO, FIM)))
                .isFalse();
    }
}
