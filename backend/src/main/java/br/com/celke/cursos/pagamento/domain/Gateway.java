package br.com.celke.cursos.pagamento.domain;

import br.com.celke.cursos.shared.auditoria.DadoSensivel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Meio de pagamento configurado pelo painel. Os campos comuns ficam em {@code configuracao} (JSON);
 * os secretos, cifrados em {@code segredos} (quem cifra e decifra é a aplicação).
 */
@Entity
@Table(name = "gateways_pagamento")
public class Gateway {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private TipoGateway tipo;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false)
    private boolean ativo;

    /** JSON com os campos não secretos. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String configuracao;

    /** IV + texto cifrado (AES-256-GCM) do JSON com os campos secretos; nulo sem segredos. */
    @DadoSensivel
    private byte[] segredos;

    @DadoSensivel
    @Column(name = "webhook_token", nullable = false, length = 64, updatable = false)
    private String webhookToken;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Gateway() {}

    /** Gateway novo nasce inativo: o admin ativa depois de testar. */
    public Gateway(TipoGateway tipo, String nome, String webhookToken, Instant agora) {
        this.tipo = tipo;
        this.nome = nome.strip();
        this.webhookToken = webhookToken;
        this.configuracao = "{}";
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    public void configurar(String nome, String configuracao, byte[] segredos, Instant agora) {
        this.nome = nome.strip();
        this.configuracao = configuracao;
        this.segredos = segredos == null ? null : segredos.clone();
        this.atualizadoEm = agora;
    }

    public void ativar(Instant agora) {
        this.ativo = true;
        this.atualizadoEm = agora;
    }

    public void desativar(Instant agora) {
        this.ativo = false;
        this.atualizadoEm = agora;
    }

    public Long getId() {
        return id;
    }

    public TipoGateway getTipo() {
        return tipo;
    }

    public String getNome() {
        return nome;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public String getConfiguracao() {
        return configuracao;
    }

    public byte[] getSegredos() {
        return segredos == null ? null : segredos.clone();
    }

    public String getWebhookToken() {
        return webhookToken;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
