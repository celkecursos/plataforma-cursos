package br.com.celke.cursos.matricula.domain;

import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/**
 * Matrícula de um usuário numa turma. Guarda só os ids do usuário e da turma: os dados deles vêm dos
 * serviços dos módulos {@code usuario} e {@code curso}.
 */
@Entity
@Table(name = "matriculas")
public class Matricula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "turma_id", nullable = false)
    private Long turmaId;

    @Column(nullable = false)
    private Instant inicio;

    /** Nulo: acesso sem prazo. */
    private Instant fim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusMatricula status;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", nullable = false, length = 20)
    private FormaPagamento formaPagamento;

    @Column(length = 100)
    private String transacao;

    @Column(name = "arquivada_em")
    private Instant arquivadaEm;

    @Column(name = "cancelada_em")
    private Instant canceladaEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Matricula() {}

    public Matricula(
            Long usuarioId,
            Long turmaId,
            Instant inicio,
            Instant fim,
            FormaPagamento formaPagamento,
            String transacao,
            Instant agora) {
        validarPeriodo(inicio, fim);
        this.usuarioId = usuarioId;
        this.turmaId = turmaId;
        this.inicio = inicio;
        this.fim = fim;
        this.status = StatusMatricula.ATIVA;
        this.formaPagamento = formaPagamento;
        this.transacao = transacao == null || transacao.isBlank() ? null : transacao.strip();
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    /** Situação no instante informado: o fim é inclusivo (vale até o último segundo). */
    public SituacaoMatricula situacao(Instant agora) {
        if (status == StatusMatricula.CANCELADA) {
            return SituacaoMatricula.CANCELADA;
        }
        if (agora.isBefore(inicio)) {
            return SituacaoMatricula.AGUARDANDO_INICIO;
        }
        if (fim != null && agora.isAfter(fim)) {
            return SituacaoMatricula.VENCIDA;
        }
        return SituacaoMatricula.VIGENTE;
    }

    public boolean isVigente(Instant agora) {
        return situacao(agora) == SituacaoMatricula.VIGENTE;
    }

    public void alterarPeriodo(Instant inicio, Instant fim, Instant agora) {
        exigirAtiva();
        validarPeriodo(inicio, fim);
        this.inicio = inicio;
        this.fim = fim;
        this.atualizadoEm = agora;
    }

    public void cancelar(Instant agora) {
        exigirAtiva();
        this.status = StatusMatricula.CANCELADA;
        this.canceladaEm = agora;
        this.atualizadoEm = agora;
    }

    /** O aluno tira o curso da lista principal; o acesso continua o mesmo. */
    public void arquivar(Instant agora) {
        if (arquivadaEm == null) {
            this.arquivadaEm = agora;
            this.atualizadoEm = agora;
        }
    }

    public void desarquivar(Instant agora) {
        if (arquivadaEm != null) {
            this.arquivadaEm = null;
            this.atualizadoEm = agora;
        }
    }

    public boolean isArquivada() {
        return arquivadaEm != null;
    }

    private void exigirAtiva() {
        if (status == StatusMatricula.CANCELADA) {
            throw new ConflitoException("A matrícula está cancelada.");
        }
    }

    private static void validarPeriodo(Instant inicio, Instant fim) {
        if (fim != null && !fim.isAfter(inicio)) {
            throw new RegraNegocioException(HttpStatus.BAD_REQUEST, "O fim da matrícula deve ser depois do início.");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getTurmaId() {
        return turmaId;
    }

    public Instant getInicio() {
        return inicio;
    }

    public Instant getFim() {
        return fim;
    }

    public StatusMatricula getStatus() {
        return status;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public String getTransacao() {
        return transacao;
    }

    public Instant getArquivadaEm() {
        return arquivadaEm;
    }

    public Instant getCanceladaEm() {
        return canceladaEm;
    }
}
