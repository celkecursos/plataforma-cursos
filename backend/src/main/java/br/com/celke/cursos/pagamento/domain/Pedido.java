package br.com.celke.cursos.pagamento.domain;

import br.com.celke.cursos.matricula.domain.FormaPagamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

/**
 * Compra de uma oferta. O {@code codigo} é público: vai no checkout (Hotmart: {@code xcod}) e volta
 * na notificação e na página de retorno. Status só muda por {@link #mudarStatus}, depois da decisão
 * da {@link TransicoesPedido}.
 */
@Entity
@Table(name = "pedidos")
public class Pedido {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 22, updatable = false)
    private String codigo;

    @Column(name = "oferta_id", nullable = false, updatable = false)
    private Long ofertaId;

    @Column(name = "gateway_id", nullable = false, updatable = false)
    private Long gatewayId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusPedido status;

    @Column(name = "transacao_externa", length = 100)
    private String transacaoExterna;

    @Column(name = "valor_centavos")
    private Long valorCentavos;

    @Column(length = 3)
    private String moeda;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", length = 20)
    private FormaPagamento formaPagamento;

    @Column(name = "matricula_id")
    private Long matriculaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private OrigemPedido origem;

    @Column(name = "ultimo_evento_em")
    private Instant ultimoEventoEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Pedido() {}

    private Pedido(Oferta oferta, Long usuarioId, String email, String nome, OrigemPedido origem, Instant agora) {
        this.codigo = novoCodigo();
        this.ofertaId = oferta.getId();
        this.gatewayId = oferta.getGatewayId();
        this.usuarioId = usuarioId;
        this.email = email.strip().toLowerCase(Locale.ROOT);
        this.nome = nome == null || nome.isBlank() ? null : truncar(nome.strip(), 120);
        this.status = StatusPedido.CRIADO;
        this.valorCentavos = oferta.getPrecoCentavos();
        this.moeda = oferta.getMoeda();
        this.origem = origem;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    /** Pedido do botão "Comprar", do aluno logado. */
    public static Pedido doCheckout(Oferta oferta, Long usuarioId, String email, String nome, Instant agora) {
        return new Pedido(oferta, usuarioId, email, nome, OrigemPedido.CHECKOUT, agora);
    }

    /** Venda que chegou pelo meio de pagamento sem o nosso código (afiliado, link direto). */
    public static Pedido externo(Oferta oferta, String email, String nome, String transacao, Instant agora) {
        Pedido pedido = new Pedido(oferta, null, email, nome, OrigemPedido.EXTERNO, agora);
        pedido.transacaoExterna = transacao;
        return pedido;
    }

    /** 16 bytes aleatórios em Base64 URL: 22 caracteres, impossível de adivinhar. */
    static String novoCodigo() {
        byte[] bytes = new byte[16];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Liga a transação do meio de pagamento ao pedido (primeira notificação). */
    public void vincularTransacao(String transacao, Instant agora) {
        if (transacaoExterna == null && transacao != null) {
            this.transacaoExterna = transacao;
            this.atualizadoEm = agora;
        }
    }

    /** Valor, moeda e forma como o meio de pagamento informou (o preço da oferta pode ter mudado ou ter cupom). */
    public void registrarPagamento(Long valorCentavos, String moeda, FormaPagamento forma, Instant agora) {
        if (valorCentavos != null) {
            this.valorCentavos = valorCentavos;
            this.moeda = moeda;
        }
        if (forma != null) {
            this.formaPagamento = forma;
        }
        this.atualizadoEm = agora;
    }

    /** Aplica uma mudança já decidida pela {@link TransicoesPedido}. */
    public void mudarStatus(StatusPedido novo, Instant ocorridoEm, Instant agora) {
        this.status = novo;
        if (ocorridoEm != null && (ultimoEventoEm == null || ocorridoEm.isAfter(ultimoEventoEm))) {
            this.ultimoEventoEm = ocorridoEm;
        }
        this.atualizadoEm = agora;
    }

    public void vincularUsuario(Long usuarioId, Instant agora) {
        this.usuarioId = usuarioId;
        this.atualizadoEm = agora;
    }

    public void vincularMatricula(Long matriculaId, Instant agora) {
        this.matriculaId = matriculaId;
        this.atualizadoEm = agora;
    }

    /** O usuário é o dono do pedido (a página de retorno mostra o curso só para ele). */
    public boolean pertenceA(Long usuarioId) {
        return this.usuarioId != null && this.usuarioId.equals(usuarioId);
    }

    private static String truncar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public Long getOfertaId() {
        return ofertaId;
    }

    public Long getGatewayId() {
        return gatewayId;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getEmail() {
        return email;
    }

    public String getNome() {
        return nome;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public String getTransacaoExterna() {
        return transacaoExterna;
    }

    public Long getValorCentavos() {
        return valorCentavos;
    }

    public String getMoeda() {
        return moeda;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public Long getMatriculaId() {
        return matriculaId;
    }

    public OrigemPedido getOrigem() {
        return origem;
    }

    public Instant getUltimoEventoEm() {
        return ultimoEventoEm;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
