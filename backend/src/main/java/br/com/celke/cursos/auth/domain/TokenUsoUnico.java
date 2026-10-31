package br.com.celke.cursos.auth.domain;

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

/** Token de link enviado por e-mail, gravado só pelo hash. Vale uma vez e até expirar. */
@Entity
@SemAuditoria
@Table(name = "tokens_uso_unico")
public class TokenUsoUnico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoToken tipo;

    @Column(nullable = false, length = 64)
    private String hash;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    /** Preenchido quando o token é usado ou invalidado por um token mais novo. */
    @Column(name = "usado_em")
    private Instant usadoEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected TokenUsoUnico() {}

    public TokenUsoUnico(Long usuarioId, TipoToken tipo, String hash, Instant expiraEm, Instant agora) {
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.hash = hash;
        this.expiraEm = expiraEm;
        this.criadoEm = agora;
    }

    /** Ainda não usado, do tipo certo e dentro da validade (expira no instante exato). */
    public boolean podeSerUsado(TipoToken tipoEsperado, Instant agora) {
        return usadoEm == null && tipo == tipoEsperado && agora.isBefore(expiraEm);
    }

    public void marcarUsado(Instant agora) {
        this.usadoEm = agora;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public TipoToken getTipo() {
        return tipo;
    }

    public String getHash() {
        return hash;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public Instant getUsadoEm() {
        return usadoEm;
    }
}
