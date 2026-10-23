package br.com.celke.cursos.forum.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Imagem anexada a um tópico ou a uma resposta, já reprocessada (JPEG sem metadados). */
@Entity
@Table(name = "forum_anexos")
public class Anexo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topico_id", updatable = false)
    private Long topicoId;

    @Column(name = "resposta_id", updatable = false)
    private Long respostaId;

    @Column(name = "autor_id", nullable = false, updatable = false)
    private Long autorId;

    @Column(nullable = false, length = 200, updatable = false)
    private String chave;

    @Column(nullable = false, updatable = false)
    private long tamanho;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected Anexo() {}

    private Anexo(Long topicoId, Long respostaId, Long autorId, String chave, long tamanho, Instant agora) {
        this.topicoId = topicoId;
        this.respostaId = respostaId;
        this.autorId = autorId;
        this.chave = chave;
        this.tamanho = tamanho;
        this.criadoEm = agora;
    }

    public static Anexo doTopico(Long topicoId, Long autorId, String chave, long tamanho, Instant agora) {
        return new Anexo(topicoId, null, autorId, chave, tamanho, agora);
    }

    public static Anexo daResposta(Long respostaId, Long autorId, String chave, long tamanho, Instant agora) {
        return new Anexo(null, respostaId, autorId, chave, tamanho, agora);
    }

    public Long getId() {
        return id;
    }

    public Long getTopicoId() {
        return topicoId;
    }

    public Long getRespostaId() {
        return respostaId;
    }

    public Long getAutorId() {
        return autorId;
    }

    public String getChave() {
        return chave;
    }

    public long getTamanho() {
        return tamanho;
    }
}
