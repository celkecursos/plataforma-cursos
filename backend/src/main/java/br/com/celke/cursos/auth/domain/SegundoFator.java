package br.com.celke.cursos.auth.domain;

import br.com.celke.cursos.shared.auditoria.SemAuditoria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Segredo TOTP de um administrador, cifrado. Fora da trilha: o último passo muda a cada login; os
 * eventos (cadastro, código errado, código de recuperação) ficam na auditoria de login.
 */
@Entity
@SemAuditoria
@Table(name = "segundo_fator")
public class SegundoFator {

    @Id
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false)
    private byte[] segredo;

    @Column(name = "ativado_em")
    private Instant ativadoEm;

    @Column(name = "ultimo_passo")
    private Long ultimoPasso;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected SegundoFator() {}

    public SegundoFator(Long usuarioId, byte[] segredo, Instant agora) {
        this.usuarioId = usuarioId;
        this.segredo = segredo;
        this.criadoEm = agora;
    }

    /** Novo segredo de um cadastro ainda não confirmado (o anterior é descartado). */
    public void trocarSegredo(byte[] segredo, Instant agora) {
        this.segredo = segredo;
        this.criadoEm = agora;
    }

    public void ativar(long passo, Instant agora) {
        this.ativadoEm = agora;
        this.ultimoPasso = passo;
    }

    public void registrarPasso(long passo) {
        this.ultimoPasso = passo;
    }

    public boolean isAtivo() {
        return ativadoEm != null;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public byte[] getSegredo() {
        return segredo;
    }

    public Long getUltimoPasso() {
        return ultimoPasso;
    }
}
