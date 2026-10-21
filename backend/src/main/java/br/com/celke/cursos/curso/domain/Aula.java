package br.com.celke.cursos.curso.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "aulas")
public class Aula implements Ordenavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "modulo_id", nullable = false)
    private Modulo modulo;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(nullable = false)
    private int ordem;

    /** Aula aberta a quem não tem matrícula (degustação). */
    @Column(nullable = false)
    private boolean gratuita;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusConteudo status;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Aula() {}

    public Aula(Modulo modulo, String titulo, String descricao, boolean gratuita, int ordem, Instant agora) {
        this.modulo = modulo;
        this.ordem = ordem;
        this.status = StatusConteudo.ATIVO;
        this.criadoEm = agora;
        atualizar(titulo, descricao, gratuita, agora);
    }

    public void atualizar(String titulo, String descricao, boolean gratuita, Instant agora) {
        this.titulo = titulo.strip();
        this.descricao = descricao == null || descricao.isBlank() ? null : descricao.strip();
        this.gratuita = gratuita;
        this.atualizadoEm = agora;
    }

    public void alterarStatus(StatusConteudo novo, Instant agora) {
        this.status = novo;
        this.atualizadoEm = agora;
    }

    @Override
    public void definirOrdem(int ordem) {
        this.ordem = ordem;
    }

    @Override
    public Long getId() {
        return id;
    }

    public Modulo getModulo() {
        return modulo;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    @Override
    public int getOrdem() {
        return ordem;
    }

    public boolean isGratuita() {
        return gratuita;
    }

    public StatusConteudo getStatus() {
        return status;
    }
}
