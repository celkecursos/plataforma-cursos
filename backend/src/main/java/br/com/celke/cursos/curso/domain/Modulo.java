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
@Table(name = "modulos")
public class Modulo implements Ordenavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turma_id", nullable = false)
    private Turma turma;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false)
    private int ordem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusConteudo status;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Modulo() {}

    public Modulo(Turma turma, String titulo, int ordem, Instant agora) {
        this.turma = turma;
        this.titulo = titulo.strip();
        this.ordem = ordem;
        this.status = StatusConteudo.ATIVO;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    public void renomear(String titulo, Instant agora) {
        this.titulo = titulo.strip();
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

    public Turma getTurma() {
        return turma;
    }

    public String getTitulo() {
        return titulo;
    }

    @Override
    public int getOrdem() {
        return ordem;
    }

    public StatusConteudo getStatus() {
        return status;
    }
}
