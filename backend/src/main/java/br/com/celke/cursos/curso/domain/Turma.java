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

/** Oferta de um curso, com os próprios módulos e aulas (e, adiante, as matrículas). */
@Entity
@Table(name = "turmas")
public class Turma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curso_id", nullable = false)
    private Curso curso;

    @Column(nullable = false, length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusTurma status;

    /** Percentual de aulas vistas que dá direito ao certificado (1 a 100). */
    @Column(name = "percentual_conclusao", nullable = false)
    private int percentualConclusao = 100;

    /** Carga horária impressa no certificado; nula, o certificado sai sem ela. */
    @Column(name = "carga_horaria_horas")
    private Integer cargaHorariaHoras;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Turma() {}

    /** Turma nova nasce em preparação. */
    public Turma(Curso curso, String nome, Instant agora) {
        this.curso = curso;
        this.nome = nome.strip();
        this.status = StatusTurma.PREPARACAO;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    public void renomear(String nome, Instant agora) {
        this.nome = nome.strip();
        this.atualizadoEm = agora;
    }

    public void alterarStatus(StatusTurma novo, Instant agora) {
        this.status = novo;
        this.atualizadoEm = agora;
    }

    /** Regra do certificado: percentual mínimo de aulas vistas e carga horária. */
    public void definirCertificado(int percentualConclusao, Integer cargaHorariaHoras, Instant agora) {
        if (percentualConclusao < 1 || percentualConclusao > 100) {
            throw new IllegalArgumentException("O percentual de conclusão vai de 1 a 100.");
        }
        if (cargaHorariaHoras != null && cargaHorariaHoras < 1) {
            throw new IllegalArgumentException("A carga horária precisa ser positiva.");
        }
        this.percentualConclusao = percentualConclusao;
        this.cargaHorariaHoras = cargaHorariaHoras;
        this.atualizadoEm = agora;
    }

    public Long getId() {
        return id;
    }

    public Curso getCurso() {
        return curso;
    }

    public String getNome() {
        return nome;
    }

    public StatusTurma getStatus() {
        return status;
    }

    public int getPercentualConclusao() {
        return percentualConclusao;
    }

    public Integer getCargaHorariaHoras() {
        return cargaHorariaHoras;
    }
}
