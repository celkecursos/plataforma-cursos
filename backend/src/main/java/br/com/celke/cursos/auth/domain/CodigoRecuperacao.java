package br.com.celke.cursos.auth.domain;

import br.com.celke.cursos.shared.auditoria.SemAuditoria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Código de recuperação do segundo fator, de uso único; o banco guarda só o hash. */
@Entity
@SemAuditoria
@Table(name = "codigos_recuperacao")
public class CodigoRecuperacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false, length = 64)
    private String hash;

    @Column(name = "usado_em")
    private Instant usadoEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected CodigoRecuperacao() {}

    public CodigoRecuperacao(Long usuarioId, String hash, Instant agora) {
        this.usuarioId = usuarioId;
        this.hash = hash;
        this.criadoEm = agora;
    }

    public void marcarUsado(Instant agora) {
        this.usadoEm = agora;
    }
}
