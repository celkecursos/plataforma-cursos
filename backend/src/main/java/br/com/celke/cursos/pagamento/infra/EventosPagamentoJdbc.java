package br.com.celke.cursos.pagamento.infra;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * O que a tabela {@code eventos_pagamento} faz fora do JPA: a inclusão idempotente
 * ({@code ON CONFLICT DO NOTHING} no único de gateway + id externo) e a retenção do corpo.
 */
@Repository
public class EventosPagamentoJdbc {

    private final JdbcClient jdbc;

    public EventosPagamentoJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Grava o evento; vazio se o mesmo id externo já tinha chegado para o gateway (duplicado). */
    public Optional<Long> inserir(NovoEvento evento) {
        return jdbc.sql("""
                        INSERT INTO eventos_pagamento
                               (gateway_id, id_externo, tipo_original, recebido_em, ocorrido_em, transacao,
                                payload, dados, status, erro)
                        VALUES (:gateway, :idExterno, :tipo, :recebidoEm, :ocorridoEm, :transacao,
                                CAST(:payload AS JSONB), CAST(:dados AS JSONB), :status, :erro)
                        ON CONFLICT (gateway_id, id_externo) DO NOTHING
                        RETURNING id
                        """)
                .param("gateway", evento.gatewayId())
                .param("idExterno", evento.idExterno())
                .param("tipo", evento.tipoOriginal())
                .param("recebidoEm", utc(evento.recebidoEm()))
                .param("ocorridoEm", evento.ocorridoEm() == null ? null : utc(evento.ocorridoEm()))
                .param("transacao", evento.transacao())
                .param("payload", evento.payload())
                .param("dados", evento.dados())
                .param("status", evento.status())
                .param("erro", evento.erro())
                .query(Long.class)
                .optional();
    }

    /** Apaga o corpo e o evento normalizado dos processados ou ignorados recebidos antes do instante. */
    public int apagarPayloads(Instant recebidosAntesDe) {
        return jdbc.sql("""
                        UPDATE eventos_pagamento SET payload = NULL, dados = NULL
                         WHERE status IN ('PROCESSADO', 'IGNORADO') AND recebido_em < :antes
                           AND (payload IS NOT NULL OR dados IS NOT NULL)
                        """).param("antes", utc(recebidosAntesDe)).update();
    }

    /**
     * Evento a gravar.
     *
     * @param payload corpo em JSON (mascarado)
     * @param dados evento normalizado em JSON; nulo quando não interpretado
     */
    public record NovoEvento(
            Long gatewayId,
            String idExterno,
            String tipoOriginal,
            Instant recebidoEm,
            Instant ocorridoEm,
            String transacao,
            String payload,
            String dados,
            String status,
            String erro) {}

    private static OffsetDateTime utc(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }
}
