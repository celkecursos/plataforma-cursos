package br.com.celke.cursos.arquivo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Arquivo de apoio de uma aula ou de um módulo (exatamente um dos dois). Guarda só os ids, sem
 * relacionamento JPA com o módulo {@code curso}.
 */
@Entity
@Table(name = "arquivos_apoio")
public class ArquivoApoio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aula_id", updatable = false)
    private Long aulaId;

    @Column(name = "modulo_id", updatable = false)
    private Long moduloId;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(name = "nome_arquivo", nullable = false, length = 255)
    private String nomeArquivo;

    @Column(nullable = false, length = 200, updatable = false)
    private String chave;

    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;

    @Column(nullable = false, updatable = false)
    private long tamanho;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected ArquivoApoio() {}

    private ArquivoApoio(
            Long aulaId,
            Long moduloId,
            String titulo,
            String nomeArquivo,
            String chave,
            FormatoArquivo formato,
            long tamanho,
            Instant agora) {
        this.aulaId = aulaId;
        this.moduloId = moduloId;
        this.titulo = titulo.strip();
        this.nomeArquivo = nomeArquivo;
        this.chave = chave;
        this.contentType = formato.contentType();
        this.tamanho = tamanho;
        this.criadoEm = agora;
    }

    public static ArquivoApoio daAula(
            Long aulaId,
            String titulo,
            String nomeArquivo,
            String chave,
            FormatoArquivo formato,
            long tamanho,
            Instant agora) {
        return new ArquivoApoio(aulaId, null, titulo, nomeArquivo, chave, formato, tamanho, agora);
    }

    public static ArquivoApoio doModulo(
            Long moduloId,
            String titulo,
            String nomeArquivo,
            String chave,
            FormatoArquivo formato,
            long tamanho,
            Instant agora) {
        return new ArquivoApoio(null, moduloId, titulo, nomeArquivo, chave, formato, tamanho, agora);
    }

    public Long getId() {
        return id;
    }

    public Long getAulaId() {
        return aulaId;
    }

    public Long getModuloId() {
        return moduloId;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getNomeArquivo() {
        return nomeArquivo;
    }

    public String getChave() {
        return chave;
    }

    public String getContentType() {
        return contentType;
    }

    public long getTamanho() {
        return tamanho;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
