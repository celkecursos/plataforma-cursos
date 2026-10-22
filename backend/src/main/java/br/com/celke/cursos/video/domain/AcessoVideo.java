package br.com.celke.cursos.video.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Abertura do vídeo de uma aula por um usuário (log de acesso, com retenção limitada). */
@Entity
@Table(name = "acessos_video")
public class AcessoVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "aula_id", nullable = false, updatable = false)
    private Long aulaId;

    @Column(nullable = false, length = 45, updatable = false)
    private String ip;

    @Column(name = "user_agent", length = 255, updatable = false)
    private String userAgent;

    @Column(name = "acessado_em", nullable = false, updatable = false)
    private Instant acessadoEm;

    protected AcessoVideo() {}

    public AcessoVideo(Long usuarioId, Long aulaId, String ip, String userAgent, Instant acessadoEm) {
        this.usuarioId = usuarioId;
        this.aulaId = aulaId;
        this.ip = ip;
        this.userAgent = userAgent;
        this.acessadoEm = acessadoEm;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getAulaId() {
        return aulaId;
    }

    public String getIp() {
        return ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getAcessadoEm() {
        return acessadoEm;
    }
}
