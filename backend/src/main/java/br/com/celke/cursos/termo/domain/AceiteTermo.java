package br.com.celke.cursos.termo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Aceite de uma versão de termo por um usuário, com IP e navegador (prova do consentimento). */
@Entity
@Table(name = "aceites_termos")
public class AceiteTermo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "termo_id", nullable = false, updatable = false)
    private Long termoId;

    @Column(nullable = false, length = 45, updatable = false)
    private String ip;

    @Column(name = "user_agent", length = 255, updatable = false)
    private String userAgent;

    @Column(name = "aceito_em", nullable = false, updatable = false)
    private Instant aceitoEm;

    protected AceiteTermo() {}

    public AceiteTermo(Long usuarioId, Long termoId, String ip, String userAgent, Instant aceitoEm) {
        this.usuarioId = usuarioId;
        this.termoId = termoId;
        this.ip = ip;
        this.userAgent = userAgent;
        this.aceitoEm = aceitoEm;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getTermoId() {
        return termoId;
    }

    public String getIp() {
        return ip;
    }

    public Instant getAceitoEm() {
        return aceitoEm;
    }
}
