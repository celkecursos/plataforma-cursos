package br.com.celke.cursos.forum.domain;

import br.com.celke.cursos.shared.texto.MarkdownSeguro;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Resposta a um tópico, de aluno ou do instrutor (ADM). */
@Entity
@Table(name = "forum_respostas")
public class Resposta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topico_id", nullable = false, updatable = false)
    private Long topicoId;

    @Column(name = "autor_id", nullable = false, updatable = false)
    private Long autorId;

    @Column(name = "corpo_markdown", nullable = false, columnDefinition = "TEXT")
    private String corpoMarkdown;

    @Column(name = "corpo_html", nullable = false, columnDefinition = "TEXT")
    private String corpoHtml;

    @Column(nullable = false, updatable = false)
    private boolean instrutor;

    @Column(nullable = false)
    private boolean oculto;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Resposta() {}

    public Resposta(Long topicoId, Long autorId, String corpoMarkdown, boolean instrutor, Instant agora) {
        this.topicoId = topicoId;
        this.autorId = autorId;
        this.corpoMarkdown = corpoMarkdown.strip();
        this.corpoHtml = MarkdownSeguro.paraHtml(this.corpoMarkdown);
        this.instrutor = instrutor;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    public void moderar(boolean oculto, Instant agora) {
        this.oculto = oculto;
        this.atualizadoEm = agora;
    }

    public Long getId() {
        return id;
    }

    public Long getTopicoId() {
        return topicoId;
    }

    public Long getAutorId() {
        return autorId;
    }

    public String getCorpoMarkdown() {
        return corpoMarkdown;
    }

    public String getCorpoHtml() {
        return corpoHtml;
    }

    public boolean isInstrutor() {
        return instrutor;
    }

    public boolean isOculto() {
        return oculto;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
