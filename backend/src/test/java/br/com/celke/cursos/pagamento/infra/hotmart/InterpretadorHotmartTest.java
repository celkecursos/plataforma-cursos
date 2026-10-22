package br.com.celke.cursos.pagamento.infra.hotmart;

import static br.com.celke.cursos.pagamento.infra.hotmart.FixturesHotmart.corpo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.matricula.domain.FormaPagamento;
import br.com.celke.cursos.pagamento.domain.EventoPagamento;
import br.com.celke.cursos.pagamento.domain.NotificacaoInvalidaException;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

class InterpretadorHotmartTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final InterpretadorHotmart interpretador = new InterpretadorHotmart(json);

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "PURCHASE_APPROVED,       APROVADO,             CARTAO",
        "PURCHASE_COMPLETE,       CONCLUIDO,            CARTAO",
        "PURCHASE_CANCELED,       CANCELADO,            CARTAO",
        "PURCHASE_REFUNDED,       REEMBOLSADO,          CARTAO",
        "PURCHASE_CHARGEBACK,     CHARGEBACK,           CARTAO",
        "PURCHASE_BILLET_PRINTED, AGUARDANDO_PAGAMENTO, BOLETO",
        "PURCHASE_PROTEST,        EM_DISPUTA,           CARTAO",
        "PURCHASE_DELAYED,        ATRASADO,             CARTAO",
        "PURCHASE_EXPIRED,        EXPIRADO,             CARTAO",
    })
    @DisplayName("cada fixture de compra vira o status interno, com transação, xcod, produto, oferta e comprador")
    void fixturesDeCompra(String evento, StatusPedido status, FormaPagamento forma) {
        EventoPagamento lido = interpretador.interpretar(corpo(evento));

        assertThat(lido.tipoOriginal()).isEqualTo(evento);
        assertThat(lido.statusPedido()).isEqualTo(status);
        assertThat(lido.idExterno()).startsWith("6c4e6a5b-");
        assertThat(lido.transacao()).isEqualTo("HP17715690036014");
        assertThat(lido.codigoPedido()).isEqualTo(FixturesHotmart.XCOD);
        assertThat(lido.produtoExterno()).isEqualTo("4774438");
        assertThat(lido.ofertaExterna()).isEqualTo("k2pasun0");
        assertThat(lido.comprador().email()).isEqualTo("comprador.teste@example.com");
        assertThat(lido.comprador().nome()).isEqualTo("Comprador de Teste");
        assertThat(lido.comprador().documentoMascarado()).isEqualTo("***09");
        assertThat(lido.valorCentavos()).isEqualTo(19_700L);
        assertThat(lido.moeda()).isEqualTo("BRL");
        assertThat(lido.formaPagamento()).isEqualTo(forma);
        assertThat(lido.ocorridoEm()).isAfter(Instant.parse("2025-10-22T00:00:00Z"));
    }

    @Test
    @DisplayName("assinatura vem quando a venda é recorrente")
    void assinatura() {
        EventoPagamento lido = interpretador.interpretar(corpo("PURCHASE_DELAYED"));

        assertThat(lido.assinatura()).isEqualTo(new EventoPagamento.Assinatura("I9OT62C3", "DELAYED"));
        assertThat(interpretador.interpretar(corpo("PURCHASE_APPROVED")).assinatura())
                .isNull();
    }

    @Test
    @DisplayName("afiliado e coprodutor: sem comprador e sem preço, data em texto")
    void afiliado() {
        EventoPagamento lido = interpretador.interpretar(corpo("PURCHASE_APPROVED_AFILIADO"));

        assertThat(lido.statusPedido()).isEqualTo(StatusPedido.APROVADO);
        assertThat(lido.comprador()).isNull();
        assertThat(lido.valorCentavos()).isNull();
        assertThat(lido.moeda()).isNull();
        assertThat(lido.formaPagamento()).isEqualTo(FormaPagamento.OUTRO);
        assertThat(lido.ocorridoEm()).isEqualTo(Instant.ofEpochMilli(1_761_140_001_000L));
    }

    @Test
    @DisplayName("evento que não é de compra é lido, mas não muda o pedido")
    void eventoNaoTratado() {
        EventoPagamento lido = interpretador.interpretar(corpo("SUBSCRIPTION_CANCELLATION"));

        assertThat(lido.tipoOriginal()).isEqualTo("SUBSCRIPTION_CANCELLATION");
        assertThat(lido.statusPedido()).isNull();
        assertThat(lido.transacao()).isNull();
        assertThat(lido.comprador()).isNull();
    }

    @ParameterizedTest(name = "{0} / {1} → {2}")
    @CsvSource(
            value = {
                "PURCHASE_NOVO_EVENTO, APPROVED,           APROVADO",
                "PURCHASE_NOVO_EVENTO, under_analisys,     AGUARDANDO_PAGAMENTO",
                "PURCHASE_NOVO_EVENTO, PARTIALLY_REFUNDED, REEMBOLSADO",
                "PURCHASE_NOVO_EVENTO, STATUS_INVENTADO,   nulo",
                "PURCHASE_NOVO_EVENTO, nulo,               nulo",
                "CLUB_FIRST_ACCESS,    APPROVED,           nulo",
                "PURCHASE_APPROVED,    CANCELLED,          APROVADO",
            },
            nullValues = "nulo")
    @DisplayName("evento de compra desconhecido usa o purchase.status; status desconhecido não quebra")
    void statusPorEventoOuCompra(String evento, String statusCompra, StatusPedido esperado) {
        assertThat(InterpretadorHotmart.status(evento, statusCompra)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(
            value = {
                "CREDIT_CARD, CARTAO",
                "credit_card, CARTAO",
                "PIX,         PIX",
                "BILLET,      BOLETO",
                "PAYPAL,      OUTRO",
                "GOOGLE_PAY,  OUTRO",
                "nulo,        nulo",
            },
            nullValues = "nulo")
    void forma(String tipo, FormaPagamento esperada) {
        assertThat(InterpretadorHotmart.forma(tipo)).isEqualTo(esperada);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            value = {
                "milissegundos,          1761140000000,             2025-10-22T13:33:20Z",
                "segundos,               1761140000,                2025-10-22T13:33:20Z",
                "ISO com fuso,           2025-10-22T10:33:20-03:00, 2025-10-22T13:33:20Z",
                "ISO em UTC,             2025-10-22T13:33:20Z,      2025-10-22T13:33:20Z",
                "texto qualquer,         ontem,                     nulo",
            },
            nullValues = "nulo")
    @DisplayName("data em número ou em texto")
    void datas(String caso, String valor, Instant esperado) {
        assertThat(InterpretadorHotmart.instante(JsonNodeFactory.instance.stringNode(valor)))
                .isEqualTo(esperado);
    }

    @Test
    @DisplayName("data como número JSON e data ausente")
    void dataNumero() {
        assertThat(InterpretadorHotmart.instante(JsonNodeFactory.instance.numberNode(1_761_140_000_000L)))
                .isEqualTo(Instant.parse("2025-10-22T13:33:20Z"));
        assertThat(InterpretadorHotmart.instante(JsonNodeFactory.instance.missingNode()))
                .isNull();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(
            value = {"197, 19700", "197.5, 19750", "0.005, 1", "19.994, 1999", "abc, nulo"},
            nullValues = "nulo")
    @DisplayName("valor para centavos, arredondando a meia unidade para cima")
    void centavos(String valor, Long esperado) {
        assertThat(InterpretadorHotmart.centavos(JsonNodeFactory.instance.stringNode(valor)))
                .isEqualTo(esperado);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "não é JSON        | {quebrado              | O corpo não é um JSON válido.",
                "lista             | []                     | O corpo não é um objeto JSON.",
                "sem id            | {\"event\":\"PURCHASE_APPROVED\"} | A notificação não tem id.",
                "sem evento        | {\"id\":\"1\"}         | A notificação não tem o nome do evento.",
                "versão 1          | {\"id\":\"1\",\"event\":\"x\",\"version\":\"1.0.0\"} | Webhook na versão 1.0.0: configure a versão 2.0.0 na Hotmart.",
            })
    @DisplayName("corpo fora do formato 2.0.0 é recusado com o motivo")
    void invalido(String caso, String corpo, String mensagem) {
        assertThatThrownBy(() -> interpretador.interpretar(corpo))
                .isInstanceOf(NotificacaoInvalidaException.class)
                .hasMessage(mensagem);
    }
}
