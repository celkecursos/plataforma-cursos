package br.com.celke.cursos.curso.domain;

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
@Table(name = "cursos")
public class Curso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(nullable = false, length = 80)
    private String slug;

    @Column(nullable = false, length = 300)
    private String resumo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusCurso status;

    /** Chave da capa no bucket ({@code capas/<curso>/<uuid>.jpg}); nula sem capa. */
    @Column(name = "capa_chave", length = 200)
    private String capaChave;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Curso() {}

    /** Curso novo nasce em rascunho, fora do catálogo. */
    public Curso(String titulo, String slug, String resumo, String descricao, Instant agora) {
        atualizar(titulo, slug, resumo, descricao, agora);
        this.status = StatusCurso.RASCUNHO;
        this.criadoEm = agora;
    }

    public void atualizar(String titulo, String slug, String resumo, String descricao, Instant agora) {
        this.titulo = titulo.strip();
        this.slug = slug;
        this.resumo = resumo.strip();
        this.descricao = descricao == null || descricao.isBlank() ? null : descricao.strip();
        this.atualizadoEm = agora;
    }

    public void alterarStatus(StatusCurso novo, Instant agora) {
        this.status = novo;
        this.atualizadoEm = agora;
    }

    public void definirCapa(String chave, Instant agora) {
        this.capaChave = chave;
        this.atualizadoEm = agora;
    }

    public boolean isPublicado() {
        return status == StatusCurso.PUBLICADO;
    }

    public Long getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getSlug() {
        return slug;
    }

    public String getResumo() {
        return resumo;
    }

    public String getDescricao() {
        return descricao;
    }

    public StatusCurso getStatus() {
        return status;
    }

    public String getCapaChave() {
        return capaChave;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
