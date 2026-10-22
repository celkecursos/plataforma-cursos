package br.com.celke.cursos.pagamento.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.pagamento.domain.TransicoesPedido.Decisao;
import br.com.celke.cursos.pagamento.domain.TransicoesPedido.EfeitoMatricula;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class TransicoesPedidoTest {

    private static final Instant ULTIMO = Instant.parse("2026-03-10T12:00:00Z");
    private static final TransicoesPedido TRANSICOES = new TransicoesPedido(false);

    /** Cada linha da tabela: de → para, permitido ou não. */
    @ParameterizedTest(name = "{0} → {1}: {2}")
    @CsvSource({
        "CRIADO,               AGUARDANDO_PAGAMENTO, true",
        "CRIADO,               APROVADO,             true",
        "CRIADO,               CONCLUIDO,            true",
        "CRIADO,               EM_DISPUTA,           false",
        "CRIADO,               ATRASADO,             false",
        "CRIADO,               REEMBOLSADO,          true",
        "CRIADO,               CHARGEBACK,           true",
        "CRIADO,               CANCELADO,            true",
        "CRIADO,               EXPIRADO,             true",
        "AGUARDANDO_PAGAMENTO, CRIADO,               false",
        "AGUARDANDO_PAGAMENTO, APROVADO,             true",
        "AGUARDANDO_PAGAMENTO, CONCLUIDO,            true",
        "AGUARDANDO_PAGAMENTO, EXPIRADO,             true",
        "AGUARDANDO_PAGAMENTO, CANCELADO,            true",
        "AGUARDANDO_PAGAMENTO, EM_DISPUTA,           false",
        "APROVADO,             AGUARDANDO_PAGAMENTO, false",
        "APROVADO,             CONCLUIDO,            true",
        "APROVADO,             EM_DISPUTA,           true",
        "APROVADO,             ATRASADO,             true",
        "APROVADO,             REEMBOLSADO,          true",
        "APROVADO,             CHARGEBACK,           true",
        "APROVADO,             CANCELADO,            true",
        "APROVADO,             EXPIRADO,             false",
        "CONCLUIDO,            APROVADO,             false",
        "CONCLUIDO,            EM_DISPUTA,           true",
        "CONCLUIDO,            ATRASADO,             true",
        "CONCLUIDO,            REEMBOLSADO,          true",
        "CONCLUIDO,            CHARGEBACK,           true",
        "EM_DISPUTA,           APROVADO,             true",
        "EM_DISPUTA,           CONCLUIDO,            true",
        "EM_DISPUTA,           REEMBOLSADO,          true",
        "EM_DISPUTA,           ATRASADO,             false",
        "ATRASADO,             APROVADO,             true",
        "ATRASADO,             EM_DISPUTA,           true",
        "ATRASADO,             CANCELADO,            true",
        "REEMBOLSADO,          APROVADO,             false",
        "REEMBOLSADO,          CONCLUIDO,            false",
        "REEMBOLSADO,          CANCELADO,            false",
        "REEMBOLSADO,          CHARGEBACK,           true",
        "CHARGEBACK,           APROVADO,             false",
        "CHARGEBACK,           REEMBOLSADO,          false",
        "CANCELADO,            APROVADO,             false",
        "CANCELADO,            REEMBOLSADO,          true",
        "EXPIRADO,             APROVADO,             true",
        "EXPIRADO,             AGUARDANDO_PAGAMENTO, false",
    })
    void tabela(StatusPedido de, StatusPedido para, boolean permitido) {
        Decisao decisao = TRANSICOES.decidir(de, para, null, ULTIMO);

        assertThat(decisao.aplica()).isEqualTo(permitido);
        if (!permitido) {
            assertThat(decisao.motivo()).isEqualTo("Mudança de " + de + " para " + para + " não é permitida.");
            assertThat(decisao.efeito()).isEqualTo(EfeitoMatricula.NENHUM);
        }
    }

    static Stream<Arguments> todosOsPares() {
        return Arrays.stream(StatusPedido.values())
                .flatMap(de -> Arrays.stream(StatusPedido.values()).map(para -> Arguments.of(de, para)));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("todosOsPares")
    @DisplayName("toda combinação segue a tabela de origens; o mesmo status nunca muda nada")
    void todasAsCombinacoes(StatusPedido de, StatusPedido para) {
        Decisao decisao = TRANSICOES.decidir(de, para, null, ULTIMO);

        assertThat(decisao.aplica())
                .isEqualTo(de != para && TransicoesPedido.ORIGENS.get(para).contains(de));
        if (de == para) {
            assertThat(decisao.motivo()).isEqualTo("O pedido já está " + de + ".");
        }
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "um segundo antes da última mudança, 2026-03-10T11:59:59Z, false",
        "no mesmo instante,                  2026-03-10T12:00:00Z, true",
        "um segundo depois,                  2026-03-10T12:00:01Z, true",
    })
    @DisplayName("evento mais antigo que a última mudança não desfaz a mais nova")
    void foraDeOrdem(String caso, Instant ocorridoEm, boolean aplica) {
        Decisao decisao = TRANSICOES.decidir(StatusPedido.APROVADO, StatusPedido.CANCELADO, ULTIMO, ocorridoEm);

        assertThat(decisao.aplica()).isEqualTo(aplica);
        if (!aplica) {
            assertThat(decisao.motivo()).isEqualTo("Evento anterior à última mudança do pedido.");
        }
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"REEMBOLSADO", "CHARGEBACK"})
    @DisplayName("reembolso e chargeback valem mesmo chegando fora de ordem")
    void reembolsoSempreVale(StatusPedido novo) {
        Decisao decisao = TRANSICOES.decidir(StatusPedido.CONCLUIDO, novo, ULTIMO, ULTIMO.minusSeconds(3600));

        assertThat(decisao.aplica()).isTrue();
        assertThat(decisao.efeito()).isEqualTo(EfeitoMatricula.CANCELAR);
    }

    @Test
    @DisplayName("sem a data do evento ou sem mudança anterior, só a tabela decide")
    void semDatas() {
        assertThat(TRANSICOES
                        .decidir(StatusPedido.CRIADO, StatusPedido.APROVADO, ULTIMO, null)
                        .aplica())
                .isTrue();
        assertThat(TRANSICOES
                        .decidir(StatusPedido.CRIADO, StatusPedido.APROVADO, null, null)
                        .aplica())
                .isTrue();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "CRIADO,               NENHUM",
        "AGUARDANDO_PAGAMENTO, NENHUM",
        "APROVADO,             CONCEDER",
        "CONCLUIDO,            CONCEDER",
        "EM_DISPUTA,           NENHUM",
        "ATRASADO,             NENHUM",
        "REEMBOLSADO,          CANCELAR",
        "CHARGEBACK,           CANCELAR",
        "CANCELADO,            CANCELAR",
        "EXPIRADO,             NENHUM",
    })
    @DisplayName("efeito de cada status na matrícula")
    void efeitos(StatusPedido status, EfeitoMatricula efeito) {
        assertThat(TRANSICOES.efeito(status)).isEqualTo(efeito);
    }

    @Test
    @DisplayName("configurado para suspender, disputa tira o acesso e a aprovação devolve")
    void disputaSuspende() {
        TransicoesPedido suspende = new TransicoesPedido(true);

        assertThat(suspende.decidir(StatusPedido.APROVADO, StatusPedido.EM_DISPUTA, null, ULTIMO)
                        .efeito())
                .isEqualTo(EfeitoMatricula.CANCELAR);
        assertThat(suspende.decidir(StatusPedido.EM_DISPUTA, StatusPedido.APROVADO, null, ULTIMO)
                        .efeito())
                .isEqualTo(EfeitoMatricula.CONCEDER);
    }
}
