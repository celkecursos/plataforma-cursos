package br.com.celke.cursos.progresso.domain;

import br.com.celke.cursos.shared.auditoria.SemAuditoria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Progresso de um aluno numa aula. Guarda só os ids, sem relacionamento JPA com outros módulos. */
@Entity
@SemAuditoria
@Table(name = "progresso_aulas")
public class ProgressoAula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "aula_id", nullable = false, updatable = false)
    private Long aulaId;

    /** Onde o aluno parou no vídeo. */
    @Column(name = "posicao_segundos", nullable = false)
    private int posicaoSegundos;

    @Column(nullable = false)
    private boolean vista;

    @Column(name = "vista_em")
    private Instant vistaEm;

    /** Marcada pelo aluno para ver de novo. */
    @Column(nullable = false)
    private boolean revisar;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected ProgressoAula() {}

    public ProgressoAula(Long usuarioId, Long aulaId, Instant agora) {
        this.usuarioId = usuarioId;
        this.aulaId = aulaId;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    /** Aplica só o que veio preenchido; desmarcar a aula vista apaga a data. */
    public void atualizar(Integer posicaoSegundos, Boolean vista, Boolean revisar, Instant agora) {
        if (posicaoSegundos != null) {
            if (posicaoSegundos < 0) {
                throw new IllegalArgumentException("A posição não pode ser negativa.");
            }
            this.posicaoSegundos = posicaoSegundos;
        }
        if (vista != null && vista != this.vista) {
            this.vista = vista;
            this.vistaEm = vista ? agora : null;
        }
        if (revisar != null) {
            this.revisar = revisar;
        }
        this.atualizadoEm = agora;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getAulaId() {
        return aulaId;
    }

    public int getPosicaoSegundos() {
        return posicaoSegundos;
    }

    public boolean isVista() {
        return vista;
    }

    public Instant getVistaEm() {
        return vistaEm;
    }

    public boolean isRevisar() {
        return revisar;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
