package br.com.celke.cursos.pagamento.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Uma linha da linha do tempo do pedido (só inclusão). */
@Entity
@Table(name = "pedido_historico")
public class PedidoHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pedido_id", nullable = false, updatable = false)
    private Long pedidoId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, updatable = false)
    private StatusPedido de;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private StatusPedido para;

    @Column(name = "evento_id", updatable = false)
    private Long eventoId;

    @Column(length = 300, updatable = false)
    private String motivo;

    @Column(nullable = false, updatable = false)
    private Instant em;

    protected PedidoHistorico() {}

    public PedidoHistorico(
            Long pedidoId, StatusPedido de, StatusPedido para, Long eventoId, String motivo, Instant em) {
        this.pedidoId = pedidoId;
        this.de = de;
        this.para = para;
        this.eventoId = eventoId;
        this.motivo = motivo == null || motivo.length() <= 300 ? motivo : motivo.substring(0, 300);
        this.em = em;
    }

    public Long getId() {
        return id;
    }

    public Long getPedidoId() {
        return pedidoId;
    }

    public StatusPedido getDe() {
        return de;
    }

    public StatusPedido getPara() {
        return para;
    }

    public Long getEventoId() {
        return eventoId;
    }

    public String getMotivo() {
        return motivo;
    }

    public Instant getEm() {
        return em;
    }
}
