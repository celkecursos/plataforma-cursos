package br.com.celke.cursos.video.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Vídeo de uma aula (no máximo um). Guarda só o id da aula, sem relacionamento JPA com o módulo
 * {@code curso}. O status é o do último envio; o HLS publicado ({@code playlistChave}) só muda quando
 * uma nova conversão termina, então um vídeo pronto continua no ar enquanto o substituto é processado.
 */
@Entity
@Table(name = "videos")
public class Video {

    /** Tamanho máximo da mensagem de erro guardada (a saída do ffmpeg pode ser longa). */
    static final int TAMANHO_ERRO = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aula_id", nullable = false, updatable = false)
    private Long aulaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusVideo status;

    @Column(name = "upload_chave", length = 200)
    private String uploadChave;

    @Column(name = "upload_id", length = 200)
    private String uploadId;

    @Column(name = "nome_original", length = 255)
    private String nomeOriginal;

    @Column(name = "tamanho_original")
    private Long tamanhoOriginal;

    @Column(name = "playlist_chave", length = 200)
    private String playlistChave;

    @Column(name = "thumbnail_chave", length = 200)
    private String thumbnailChave;

    @Column(name = "tamanho_final")
    private Long tamanhoFinal;

    @Column(name = "duracao_segundos")
    private Integer duracaoSegundos;

    private Integer largura;

    private Integer altura;

    /** Alturas geradas, da menor para a maior (ex.: {@code 720p,1080p}). */
    @Column(length = 50)
    private String variantes;

    @Column(name = "tempo_processamento_ms")
    private Long tempoProcessamentoMs;

    @Column(name = "processado_em")
    private Instant processadoEm;

    @Column(columnDefinition = "TEXT")
    private String erro;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Video() {}

    public Video(Long aulaId, Instant agora) {
        this.aulaId = aulaId;
        this.status = StatusVideo.AGUARDANDO_ENVIO;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    /** Novo envio: guarda o upload em partes; o HLS publicado, se houver, continua no ar. */
    public void iniciarEnvio(String chave, String uploadId, String nomeOriginal, long tamanho, Instant agora) {
        exigirForaDoProcessamento();
        this.status = StatusVideo.AGUARDANDO_ENVIO;
        this.uploadChave = chave;
        this.uploadId = uploadId;
        this.nomeOriginal = nomeOriginal;
        this.tamanhoOriginal = tamanho;
        this.erro = null;
        this.atualizadoEm = agora;
    }

    /** Arquivo conferido: vai para a fila. O upload em partes já foi concluído. */
    public void enfileirar(long tamanhoReal, Instant agora) {
        if (status != StatusVideo.AGUARDANDO_ENVIO) {
            throw new IllegalStateException("Só um envio em andamento pode ir para a fila.");
        }
        this.status = StatusVideo.PENDENTE;
        this.uploadId = null;
        this.tamanhoOriginal = tamanhoReal;
        this.atualizadoEm = agora;
    }

    /** Volta para a fila a partir do original que ainda está no bucket. */
    public void reprocessar(Instant agora) {
        if (status != StatusVideo.ERRO) {
            throw new IllegalStateException("Só um vídeo com erro pode ser reprocessado.");
        }
        this.status = StatusVideo.PENDENTE;
        this.erro = null;
        this.atualizadoEm = agora;
    }

    public void iniciarProcessamento(Instant agora) {
        this.status = StatusVideo.PROCESSANDO;
        this.atualizadoEm = agora;
    }

    /**
     * Conversão concluída: publica o HLS novo e esquece o original (apagado do bucket em seguida).
     * Devolve a chave da playlist anterior, cujos arquivos devem ser apagados.
     */
    public String concluir(ResultadoTranscodificacao resultado, Instant agora) {
        String anterior = playlistChave;
        this.status = StatusVideo.PRONTO;
        this.playlistChave = resultado.playlistChave();
        this.thumbnailChave = resultado.thumbnailChave();
        this.tamanhoFinal = resultado.tamanhoFinal();
        this.duracaoSegundos = resultado.duracaoSegundos();
        this.largura = resultado.largura();
        this.altura = resultado.altura();
        this.variantes = resultado.variantes();
        this.tempoProcessamentoMs = resultado.tempoProcessamentoMs();
        this.processadoEm = agora;
        this.uploadChave = null;
        this.erro = null;
        this.atualizadoEm = agora;
        return anterior;
    }

    /** Falha no envio ou na conversão; o original continua no bucket enquanto existir (para reprocessar). */
    public void falhar(String mensagem, Instant agora) {
        this.status = StatusVideo.ERRO;
        this.erro = mensagem == null || mensagem.length() <= TAMANHO_ERRO
                ? mensagem
                : mensagem.substring(mensagem.length() - TAMANHO_ERRO);
        this.atualizadoEm = agora;
    }

    /** O original foi recusado e apagado: sem ele, só um envio novo resolve. */
    public void descartarOriginal(Instant agora) {
        this.uploadChave = null;
        this.uploadId = null;
        this.atualizadoEm = agora;
    }

    public void exigirForaDoProcessamento() {
        if (status.emProcessamento()) {
            throw new IllegalStateException("O vídeo está em processamento.");
        }
    }

    /** Redução de tamanho do original para o HLS, em percentual (negativo se cresceu); vazio sem os dois tamanhos. */
    public Double reducaoPercentual() {
        if (tamanhoOriginal == null || tamanhoFinal == null || status != StatusVideo.PRONTO) {
            return null;
        }
        return Math.round((1 - (double) tamanhoFinal / tamanhoOriginal) * 1000) / 10.0;
    }

    /** Segundos de processamento por minuto de vídeo (medida do capítulo de limites da VPS). */
    public Double segundosPorMinutoDeVideo() {
        if (tempoProcessamentoMs == null || duracaoSegundos == null || duracaoSegundos == 0) {
            return null;
        }
        return Math.round(tempoProcessamentoMs / 1000.0 / (duracaoSegundos / 60.0) * 10) / 10.0;
    }

    public Long getId() {
        return id;
    }

    public Long getAulaId() {
        return aulaId;
    }

    public StatusVideo getStatus() {
        return status;
    }

    public String getUploadChave() {
        return uploadChave;
    }

    public String getUploadId() {
        return uploadId;
    }

    public String getNomeOriginal() {
        return nomeOriginal;
    }

    public Long getTamanhoOriginal() {
        return tamanhoOriginal;
    }

    public String getPlaylistChave() {
        return playlistChave;
    }

    public String getThumbnailChave() {
        return thumbnailChave;
    }

    public Long getTamanhoFinal() {
        return tamanhoFinal;
    }

    public Integer getDuracaoSegundos() {
        return duracaoSegundos;
    }

    public Integer getLargura() {
        return largura;
    }

    public Integer getAltura() {
        return altura;
    }

    public String getVariantes() {
        return variantes;
    }

    public Long getTempoProcessamentoMs() {
        return tempoProcessamentoMs;
    }

    public Instant getProcessadoEm() {
        return processadoEm;
    }

    public String getErro() {
        return erro;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
