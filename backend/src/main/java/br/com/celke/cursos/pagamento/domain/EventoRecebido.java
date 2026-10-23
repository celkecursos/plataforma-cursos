package br.com.celke.cursos.pagamento.domain;

import br.com.celke.cursos.shared.auditoria.SemAuditoria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Notificação gravada (tabela {@code eventos_pagamento}). A inclusão é feita com
 * {@code INSERT ... ON CONFLICT DO NOTHING} pelo repositório (idempotência); o corpo e o evento
 * normalizado só são escritos ali e apagados pela retenção.
 */
@Entity
@SemAuditoria
@Table(name = "eventos_pagamento")
public class EventoRecebido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gateway_id", nullable = false, updatable = false)
    private Long gatewayId;

    @Column(name = "id_externo", nullable = false, length = 150, updatable = false)
    private String idExterno;

    @Column(name = "tipo_original", nullable = false, length = 60, updatable = false)
    private String tipoOriginal;

    @Column(name = "recebido_em", nullable = false, updatable = false)
    private Instant recebidoEm;

    @Column(name = "ocorrido_em", updatable = false)
    private Instant ocorridoEm;

    @Column(length = 100, updatable = false)
    private String transacao;

    /** Corpo recebido (mascarado), em JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(insertable = false, updatable = false)
    private String payload;

    /** {@link EventoPagamento} em JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(insertable = false, updatable = false)
    private String dados;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusEvento status;

    private String erro;

    @Column(name = "pedido_id")
    private Long pedidoId;

    @Column(name = "processado_em")
    private Instant processadoEm;

    protected EventoRecebido() {}

    public void processado(Long pedidoId, Instant agora) {
        this.status = StatusEvento.PROCESSADO;
        this.pedidoId = pedidoId;
        this.erro = null;
        this.processadoEm = agora;
    }

    public void ignorado(Long pedidoId, String motivo, Instant agora) {
        this.status = StatusEvento.IGNORADO;
        this.pedidoId = pedidoId;
        this.erro = motivo;
        this.processadoEm = agora;
    }

    public void falhou(String motivo, Instant agora) {
        this.status = StatusEvento.ERRO;
        this.erro = motivo;
        this.processadoEm = agora;
    }

    /** Volta para a fila (reprocessamento pelo painel). */
    public void reabrir() {
        this.status = StatusEvento.RECEBIDO;
        this.erro = null;
        this.processadoEm = null;
    }

    public Long getId() {
        return id;
    }

    public Long getGatewayId() {
        return gatewayId;
    }

    public String getIdExterno() {
        return idExterno;
    }

    public String getTipoOriginal() {
        return tipoOriginal;
    }

    public Instant getRecebidoEm() {
        return recebidoEm;
    }

    public Instant getOcorridoEm() {
        return ocorridoEm;
    }

    public String getTransacao() {
        return transacao;
    }

    public String getPayload() {
        return payload;
    }

    public String getDados() {
        return dados;
    }

    public StatusEvento getStatus() {
        return status;
    }

    public String getErro() {
        return erro;
    }

    public Long getPedidoId() {
        return pedidoId;
    }

    public Instant getProcessadoEm() {
        return processadoEm;
    }
}
