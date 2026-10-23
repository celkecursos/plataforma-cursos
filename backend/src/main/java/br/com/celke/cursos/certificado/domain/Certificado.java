package br.com.celke.cursos.certificado.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Certificado emitido para um aluno numa turma, com os dados impressos congelados na emissão. */
@Entity
@Table(name = "certificados")
public class Certificado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "turma_id", nullable = false, updatable = false)
    private Long turmaId;

    @Column(nullable = false, length = 20, updatable = false)
    private String codigo;

    @Column(name = "nome_completo", nullable = false, length = 150, updatable = false)
    private String nomeCompleto;

    @Column(name = "curso_titulo", nullable = false, length = 120, updatable = false)
    private String cursoTitulo;

    @Column(name = "turma_nome", nullable = false, length = 120, updatable = false)
    private String turmaNome;

    @Column(name = "carga_horaria_horas", updatable = false)
    private Integer cargaHorariaHoras;

    @Column(name = "percentual_concluido", nullable = false, updatable = false)
    private int percentualConcluido;

    @Column(name = "pdf_chave", nullable = false, length = 200, updatable = false)
    private String pdfChave;

    @Column(name = "emitido_em", nullable = false, updatable = false)
    private Instant emitidoEm;

    protected Certificado() {}

    public Certificado(
            Long usuarioId,
            Long turmaId,
            String codigo,
            DadosImpressos dados,
            int percentualConcluido,
            String pdfChave,
            Instant emitidoEm) {
        this.usuarioId = usuarioId;
        this.turmaId = turmaId;
        this.codigo = codigo;
        this.nomeCompleto = dados.nomeCompleto();
        this.cursoTitulo = dados.cursoTitulo();
        this.turmaNome = dados.turmaNome();
        this.cargaHorariaHoras = dados.cargaHorariaHoras();
        this.percentualConcluido = percentualConcluido;
        this.pdfChave = pdfChave;
        this.emitidoEm = emitidoEm;
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

    public String getCodigo() {
        return codigo;
    }

    public String getNomeCompleto() {
        return nomeCompleto;
    }

    public String getCursoTitulo() {
        return cursoTitulo;
    }

    public String getTurmaNome() {
        return turmaNome;
    }

    public Integer getCargaHorariaHoras() {
        return cargaHorariaHoras;
    }

    public int getPercentualConcluido() {
        return percentualConcluido;
    }

    public String getPdfChave() {
        return pdfChave;
    }

    public Instant getEmitidoEm() {
        return emitidoEm;
    }

    /** O que vai impresso no certificado. */
    public record DadosImpressos(
            String nomeCompleto, String cursoTitulo, String turmaNome, Integer cargaHorariaHoras) {}
}
