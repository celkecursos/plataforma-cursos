package br.com.celke.cursos.pagamento.infra.hotmart;

import br.com.celke.cursos.matricula.domain.FormaPagamento;
import br.com.celke.cursos.pagamento.application.MascaraDadosPessoais;
import br.com.celke.cursos.pagamento.domain.EventoPagamento;
import br.com.celke.cursos.pagamento.domain.EventoPagamento.Assinatura;
import br.com.celke.cursos.pagamento.domain.EventoPagamento.CompradorEvento;
import br.com.celke.cursos.pagamento.domain.NotificacaoInvalidaException;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Traduz o webhook 2.0.0 da Hotmart ({@code {id, creation_date, event, version, data}}) para o
 * {@link EventoPagamento}. Tolerante: status desconhecido não quebra, campos de afiliado e coprodutor
 * (sem {@code buyer} e sem {@code price}) são opcionais e a data vem em milissegundos ou em texto.
 */
public class InterpretadorHotmart {

    /** O que cada evento de compra faz com o pedido. */
    static final Map<String, StatusPedido> POR_EVENTO = Map.of(
            "PURCHASE_APPROVED", StatusPedido.APROVADO,
            "PURCHASE_COMPLETE", StatusPedido.CONCLUIDO,
            "PURCHASE_CANCELED", StatusPedido.CANCELADO,
            "PURCHASE_REFUNDED", StatusPedido.REEMBOLSADO,
            "PURCHASE_CHARGEBACK", StatusPedido.CHARGEBACK,
            "PURCHASE_BILLET_PRINTED", StatusPedido.AGUARDANDO_PAGAMENTO,
            "PURCHASE_PROTEST", StatusPedido.EM_DISPUTA,
            "PURCHASE_DELAYED", StatusPedido.ATRASADO,
            "PURCHASE_EXPIRED", StatusPedido.EXPIRADO);

    /** {@code data.purchase.status}, usado quando o evento de compra não está na tabela acima. */
    static final Map<String, StatusPedido> POR_STATUS = Map.ofEntries(
            Map.entry("APPROVED", StatusPedido.APROVADO),
            Map.entry("COMPLETE", StatusPedido.CONCLUIDO),
            Map.entry("CANCELLED", StatusPedido.CANCELADO),
            Map.entry("BLOCKED", StatusPedido.CANCELADO),
            Map.entry("NO_FUNDS", StatusPedido.CANCELADO),
            Map.entry("CHARGEBACK", StatusPedido.CHARGEBACK),
            Map.entry("REFUNDED", StatusPedido.REEMBOLSADO),
            Map.entry("PARTIALLY_REFUNDED", StatusPedido.REEMBOLSADO),
            Map.entry("DISPUTE", StatusPedido.EM_DISPUTA),
            Map.entry("OVERDUE", StatusPedido.ATRASADO),
            Map.entry("EXPIRED", StatusPedido.EXPIRADO),
            Map.entry("PRINTED_BILLET", StatusPedido.AGUARDANDO_PAGAMENTO),
            Map.entry("WAITING_PAYMENT", StatusPedido.AGUARDANDO_PAGAMENTO),
            Map.entry("STARTED", StatusPedido.AGUARDANDO_PAGAMENTO),
            // Grafia da documentação da Hotmart, e a correta por garantia
            Map.entry("UNDER_ANALISYS", StatusPedido.AGUARDANDO_PAGAMENTO),
            Map.entry("UNDER_ANALYSIS", StatusPedido.AGUARDANDO_PAGAMENTO),
            Map.entry("PROCESSING_TRANSACTION", StatusPedido.AGUARDANDO_PAGAMENTO));

    private final JsonMapper jsonMapper;

    public InterpretadorHotmart(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /** @throws NotificacaoInvalidaException corpo que não é JSON, sem id, sem evento ou de outra versão */
    public EventoPagamento interpretar(String corpo) {
        JsonNode raiz;
        try {
            raiz = jsonMapper.readTree(corpo);
        } catch (JacksonException e) {
            throw new NotificacaoInvalidaException("O corpo não é um JSON válido.", e);
        }
        if (raiz == null || !raiz.isObject()) {
            throw new NotificacaoInvalidaException("O corpo não é um objeto JSON.");
        }
        String id = texto(raiz.path("id"));
        String evento = texto(raiz.path("event"));
        String versao = texto(raiz.path("version"));
        if (id == null) {
            throw new NotificacaoInvalidaException("A notificação não tem id.");
        }
        if (evento == null) {
            throw new NotificacaoInvalidaException("A notificação não tem o nome do evento.");
        }
        if (versao != null && !versao.startsWith("2.")) {
            throw new NotificacaoInvalidaException(
                    "Webhook na versão " + versao + ": configure a versão 2.0.0 na Hotmart.");
        }
        JsonNode dados = raiz.path("data");
        JsonNode compra = dados.path("purchase");
        return new EventoPagamento(
                id,
                evento,
                instante(raiz.path("creation_date")),
                texto(compra.path("transaction")),
                status(evento, texto(compra.path("status"))),
                texto(compra.path("origin").path("xcod")),
                texto(dados.path("product").path("id")),
                texto(compra.path("offer").path("code")),
                comprador(dados.path("buyer")),
                centavos(compra.path("price").path("value")),
                maiusculas(texto(compra.path("price").path("currency_value"))),
                forma(texto(compra.path("payment").path("type"))),
                assinatura(dados.path("subscription")));
    }

    /** Status interno do evento; nulo para os que não mudam o pedido (assinatura, área de membros...). */
    static StatusPedido status(String evento, String statusCompra) {
        StatusPedido peloEvento = POR_EVENTO.get(evento);
        if (peloEvento != null) {
            return peloEvento;
        }
        if (evento.startsWith("PURCHASE_") && statusCompra != null) {
            return POR_STATUS.get(statusCompra.toUpperCase(Locale.ROOT));
        }
        return null;
    }

    static FormaPagamento forma(String tipo) {
        if (tipo == null) {
            return null;
        }
        return switch (tipo.toUpperCase(Locale.ROOT)) {
            case "CREDIT_CARD" -> FormaPagamento.CARTAO;
            case "PIX" -> FormaPagamento.PIX;
            case "BILLET" -> FormaPagamento.BOLETO;
            default -> FormaPagamento.OUTRO;
        };
    }

    /** Milissegundos desde 1970 (número ou texto) ou data ISO-8601; nulo se não der para ler. */
    static Instant instante(JsonNode no) {
        String valor = texto(no);
        if (valor == null) {
            return null;
        }
        if (valor.chars().allMatch(Character::isDigit)) {
            long numero = Long.parseLong(valor);
            // Menos de 12 dígitos só pode estar em segundos
            return numero < 100_000_000_000L ? Instant.ofEpochSecond(numero) : Instant.ofEpochMilli(numero);
        }
        try {
            return OffsetDateTime.parse(valor).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return Instant.parse(valor);
            } catch (DateTimeParseException naoEhData) {
                return null;
            }
        }
    }

    /** Valor em reais (ou outra moeda) para centavos; nulo se ausente ou ilegível. */
    static Long centavos(JsonNode no) {
        String valor = texto(no);
        if (valor == null) {
            return null;
        }
        try {
            return new BigDecimal(valor)
                    .movePointRight(2)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    private static CompradorEvento comprador(JsonNode comprador) {
        if (!comprador.isObject()) {
            return null;
        }
        String email = texto(comprador.path("email"));
        return new CompradorEvento(
                email == null ? null : email.toLowerCase(Locale.ROOT),
                texto(comprador.path("name")),
                MascaraDadosPessoais.mascarar(texto(comprador.path("document"))));
    }

    private static Assinatura assinatura(JsonNode assinatura) {
        if (!assinatura.isObject()) {
            return null;
        }
        String codigo = texto(assinatura.path("subscriber").path("code"));
        String status = texto(assinatura.path("status"));
        return codigo == null && status == null ? null : new Assinatura(codigo, status);
    }

    private static String maiusculas(String texto) {
        return texto == null ? null : texto.toUpperCase(Locale.ROOT);
    }

    /** Texto do nó (número vira texto); nulo se ausente, nulo, vazio ou não for um valor simples. */
    static String texto(JsonNode no) {
        if (no == null || no.isMissingNode() || no.isNull() || !no.isValueNode()) {
            return null;
        }
        String valor = no.asString();
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
