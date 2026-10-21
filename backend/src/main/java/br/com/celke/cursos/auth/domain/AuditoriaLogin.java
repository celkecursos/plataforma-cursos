package br.com.celke.cursos.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "auditoria_login")
public class AuditoriaLogin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ResultadoLogin resultado;

    @Column(nullable = false, length = 45)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected AuditoriaLogin() {}

    public AuditoriaLogin(
            String email, Long usuarioId, ResultadoLogin resultado, String ip, String userAgent, Instant agora) {
        this.email = email;
        this.usuarioId = usuarioId;
        this.resultado = resultado;
        this.ip = ip;
        this.userAgent = userAgent;
        this.criadoEm = agora;
    }

    public String getEmail() {
        return email;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public ResultadoLogin getResultado() {
        return resultado;
    }

    public String getIp() {
        return ip;
    }

    public String getUserAgent() {
        return userAgent;
    }
}
