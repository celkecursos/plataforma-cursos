package br.com.celke.cursos.termo.domain;

import br.com.celke.cursos.shared.texto.MarkdownSeguro;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Versão publicada de um termo. Não muda depois de publicada: mudar o texto é publicar outra versão. */
@Entity
@Table(name = "termos")
public class Termo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private TipoTermo tipo;

    @Column(nullable = false, updatable = false)
    private int versao;

    @Column(name = "conteudo_markdown", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String conteudoMarkdown;

    @Column(name = "conteudo_html", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String conteudoHtml;

    @Column(name = "publicado_por", nullable = false, updatable = false)
    private Long publicadoPor;

    @Column(name = "publicado_em", nullable = false, updatable = false)
    private Instant publicadoEm;

    protected Termo() {}

    public Termo(TipoTermo tipo, int versao, String conteudoMarkdown, Long publicadoPor, Instant agora) {
        this.tipo = tipo;
        this.versao = versao;
        this.conteudoMarkdown = conteudoMarkdown.strip();
        this.conteudoHtml = MarkdownSeguro.paraHtml(this.conteudoMarkdown);
        this.publicadoPor = publicadoPor;
        this.publicadoEm = agora;
    }

    public Long getId() {
        return id;
    }

    public TipoTermo getTipo() {
        return tipo;
    }

    public int getVersao() {
        return versao;
    }

    public String getConteudoHtml() {
        return conteudoHtml;
    }

    public Instant getPublicadoEm() {
        return publicadoEm;
    }
}
