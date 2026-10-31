package br.com.celke.cursos.auth.domain;

import br.com.celke.cursos.shared.auditoria.SemAuditoria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Refresh token gravado só pelo hash. Um token pertence a uma família (uma sessão de login). */
@Entity
@SemAuditoria
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false)
    private UUID familia;

    @Column(nullable = false, length = 64)
    private String hash;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "usado_em")
    private Instant usadoEm;

    @Column(name = "revogado_em")
    private Instant revogadoEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected RefreshToken() {}

    public RefreshToken(Long usuarioId, UUID familia, String hash, Instant expiraEm, Instant agora) {
        this.usuarioId = usuarioId;
        this.familia = familia;
        this.hash = hash;
        this.expiraEm = expiraEm;
        this.criadoEm = agora;
    }

    public boolean foiUsado() {
        return usadoEm != null;
    }

    public boolean estaRevogado() {
        return revogadoEm != null;
    }

    /** Expira no instante exato de {@code expiraEm}. */
    public boolean estaExpirado(Instant agora) {
        return !agora.isBefore(expiraEm);
    }

    public void marcarUsado(Instant agora) {
        this.usadoEm = agora;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public UUID getFamilia() {
        return familia;
    }

    public String getHash() {
        return hash;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }
}
