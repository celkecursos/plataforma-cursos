package br.com.celke.cursos.forum.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Pergunta no fórum da turma, geral ou de uma aula. Guarda o Markdown escrito e o HTML já filtrado;
 * só o HTML filtrado é entregue a quem lê.
 */
@Entity
@Table(name = "forum_topicos")
public class Topico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "turma_id", nullable = false, updatable = false)
    private Long turmaId;

    @Column(name = "aula_id", updatable = false)
    private Long aulaId;

    @Column(name = "autor_id", nullable = false, updatable = false)
    private Long autorId;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(name = "corpo_markdown", nullable = false, columnDefinition = "TEXT")
    private String corpoMarkdown;

    @Column(name = "corpo_html", nullable = false, columnDefinition = "TEXT")
    private String corpoHtml;

    @Column(nullable = false)
    private boolean oculto;

    @Column(nullable = false)
    private boolean fixado;

    /** Já tem resposta do instrutor (destaque na lista). */
    @Column(name = "respondido_instrutor", nullable = false)
    private boolean respondidoInstrutor;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Topico() {}

    public Topico(Long turmaId, Long aulaId, Long autorId, String titulo, String corpoMarkdown, Instant agora) {
        this.turmaId = turmaId;
        this.aulaId = aulaId;
        this.autorId = autorId;
        this.titulo = titulo.strip();
        this.corpoMarkdown = corpoMarkdown.strip();
        this.corpoHtml = MarkdownSeguro.paraHtml(this.corpoMarkdown);
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    /** Moderação: só os campos preenchidos mudam. */
    public void moderar(Boolean oculto, Boolean fixado, Instant agora) {
        if (oculto != null) {
            this.oculto = oculto;
        }
        if (fixado != null) {
            this.fixado = fixado;
        }
        this.atualizadoEm = agora;
    }

    public void registrarRespostaInstrutor(Instant agora) {
        this.respondidoInstrutor = true;
        this.atualizadoEm = agora;
    }

    public Long getId() {
        return id;
    }

    public Long getTurmaId() {
        return turmaId;
    }

    public Long getAulaId() {
        return aulaId;
    }

    public Long getAutorId() {
        return autorId;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getCorpoMarkdown() {
        return corpoMarkdown;
    }

    public String getCorpoHtml() {
        return corpoHtml;
    }

    public boolean isOculto() {
        return oculto;
    }

    public boolean isFixado() {
        return fixado;
    }

    public boolean isRespondidoInstrutor() {
        return respondidoInstrutor;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
