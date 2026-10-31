package br.com.celke.cursos.auditoria.domain;

import br.com.celke.cursos.shared.auditoria.OperacaoAuditoria;
import br.com.celke.cursos.shared.auditoria.SemAuditoria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Uma linha da trilha de auditoria, só para leitura: quem grava é o {@code TrilhaAuditoria} e o banco
 * recusa alteração e exclusão (só a retenção apaga).
 */
@Entity
@Immutable
@SemAuditoria
@Table(name = "auditoria")
public class RegistroAuditoria {

    @Id
    private Long id;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    private OperacaoAuditoria operacao;

    private String entidade;

    @Column(name = "entidade_id")
    private String entidadeId;

    /** Campos antes da mudança, em JSON; nulo na inclusão. */
    @JdbcTypeCode(SqlTypes.JSON)
    private String antes;

    /** Campos depois da mudança, em JSON; nulo na exclusão. */
    @JdbcTypeCode(SqlTypes.JSON)
    private String depois;

    private String origem;

    private String ip;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "request_id")
    private String requestId;

    private Instant em;

    protected RegistroAuditoria() {}

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public OperacaoAuditoria getOperacao() {
        return operacao;
    }

    public String getEntidade() {
        return entidade;
    }

    public String getEntidadeId() {
        return entidadeId;
    }

    public String getAntes() {
        return antes;
    }

    public String getDepois() {
        return depois;
    }

    public String getOrigem() {
        return origem;
    }

    public String getIp() {
        return ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getRequestId() {
        return requestId;
    }

    public Instant getEm() {
        return em;
    }
}
