package br.com.celke.cursos.matricula.domain;

import static br.com.celke.cursos.support.fixtures.MatriculaFixture.cancelada;
import static br.com.celke.cursos.support.fixtures.MatriculaFixture.matricula;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class MatriculaTest {

    private static final Instant INICIO = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant FIM = Instant.parse("2026-06-01T10:00:00Z");

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "um segundo antes do início,   2026-03-01T09:59:59Z, AGUARDANDO_INICIO",
        "exatamente no início,         2026-03-01T10:00:00Z, VIGENTE",
        "no meio do período,           2026-04-15T00:00:00Z, VIGENTE",
        "exatamente no fim,            2026-06-01T10:00:00Z, VIGENTE",
        "um segundo depois do fim,     2026-06-01T10:00:01Z, VENCIDA",
    })
    void situacaoPeloPeriodo(String caso, Instant agora, SituacaoMatricula esperada) {
        assertThat(matricula(1, 2, 3, INICIO, FIM).situacao(agora)).isEqualTo(esperada);
    }

    @Test
    @DisplayName("sem fim, não vence")
    void semFim() {
        assertThat(matricula(1, 2, 3, INICIO, null).situacao(Instant.parse("2099-01-01T00:00:00Z")))
                .isEqualTo(SituacaoMatricula.VIGENTE);
    }

    @Test
    @DisplayName("cancelada é cancelada em qualquer instante, mesmo dentro do período")
    void canceladaEmQualquerInstante() {
        Matricula matricula = cancelada(matricula(1, 2, 3, INICIO, FIM));
        assertThat(matricula.situacao(Instant.parse("2026-04-15T00:00:00Z"))).isEqualTo(SituacaoMatricula.CANCELADA);
        assertThat(matricula.getStatus()).isEqualTo(StatusMatricula.CANCELADA);
        assertThat(matricula.getCanceladaEm()).isNotNull();
    }

    @ParameterizedTest(name = "fim {0}")
    @CsvSource({"2026-03-01T10:00:00Z", "2026-02-28T10:00:00Z"})
    void fimPrecisaSerDepoisDoInicio(Instant fim) {
        assertThatThrownBy(() -> matricula(1, 2, 3, INICIO, fim))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("O fim da matrícula deve ser depois do início.");
    }

    @Test
    @DisplayName("um segundo depois do início já é um fim válido")
    void fimUmSegundoDepois() {
        assertThat(matricula(1, 2, 3, INICIO, INICIO.plusSeconds(1)).getFim()).isEqualTo(INICIO.plusSeconds(1));
    }

    @Test
    @DisplayName("transação em branco vira nula; preenchida é aparada")
    void transacao() {
        Instant agora = Instant.parse("2026-01-01T00:00:00Z");
        assertThat(new Matricula(1L, 2L, INICIO, null, FormaPagamento.PIX, "  ", agora).getTransacao())
                .isNull();
        assertThat(new Matricula(1L, 2L, INICIO, null, FormaPagamento.PIX, " tx-1 ", agora).getTransacao())
                .isEqualTo("tx-1");
    }

    @Nested
    class AlterarPeriodo {

        @Test
        @DisplayName("altera o início e o fim")
        void altera() {
            Matricula matricula = matricula(1, 2, 3, INICIO, FIM);
            Instant novoFim = FIM.plusSeconds(86_400);
            matricula.alterarPeriodo(INICIO, novoFim, INICIO);
            assertThat(matricula.getFim()).isEqualTo(novoFim);
        }

        @Test
        @DisplayName("recusa período invertido sem mudar nada")
        void recusaInvertido() {
            Matricula matricula = matricula(1, 2, 3, INICIO, FIM);
            assertThatThrownBy(() -> matricula.alterarPeriodo(FIM, INICIO, INICIO))
                    .isInstanceOf(RegraNegocioException.class);
            assertThat(matricula.getInicio()).isEqualTo(INICIO);
        }

        @Test
        @DisplayName("matrícula cancelada não muda de período nem é cancelada de novo")
        void naoAlteraCancelada() {
            Matricula matricula = cancelada(matricula(1, 2, 3, INICIO, FIM));
            assertThatThrownBy(() -> matricula.alterarPeriodo(INICIO, null, INICIO))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("A matrícula está cancelada.");
            assertThatThrownBy(() -> matricula.cancelar(INICIO)).isInstanceOf(ConflitoException.class);
        }
    }

    @Test
    @DisplayName("arquivar e desarquivar não mudam a situação e são idempotentes")
    void arquivar() {
        Matricula matricula = matricula(1, 2, 3, INICIO, FIM);
        Instant agora = Instant.parse("2026-04-15T00:00:00Z");
        matricula.arquivar(agora);
        matricula.arquivar(agora.plusSeconds(60));
        assertThat(matricula.getArquivadaEm()).isEqualTo(agora);
        assertThat(matricula.isVigente(agora)).isTrue();
        matricula.desarquivar(agora);
        matricula.desarquivar(agora);
        assertThat(matricula.isArquivada()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(FiltroMeusCursos.class)
    void canceladaNaoApareceEmNenhumaAba(FiltroMeusCursos filtro) {
        assertThat(filtro.aceita(cancelada(matricula(1, 2, 3, INICIO, FIM)), Instant.parse("2026-04-15T00:00:00Z")))
                .isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "vigente,                       2026-04-15T00:00:00Z, false, ATIVOS",
        "aguardando início,             2026-02-01T00:00:00Z, false, ATIVOS",
        "vigente arquivada,             2026-04-15T00:00:00Z, true,  ARQUIVADOS",
        "aguardando início arquivada,   2026-02-01T00:00:00Z, true,  ARQUIVADOS",
        "vencida,                       2026-07-01T00:00:00Z, false, EXPIRADOS",
        "vencida arquivada,             2026-07-01T00:00:00Z, true,  EXPIRADOS",
    })
    void abaDeMeusCursos(String caso, Instant agora, boolean arquivada, FiltroMeusCursos esperado) {
        Matricula matricula = matricula(1, 2, 3, INICIO, FIM);
        if (arquivada) {
            matricula.arquivar(INICIO);
        }
        for (FiltroMeusCursos filtro : FiltroMeusCursos.values()) {
            assertThat(filtro.aceita(matricula, agora)).as(filtro.name()).isEqualTo(filtro == esperado);
        }
    }
}
