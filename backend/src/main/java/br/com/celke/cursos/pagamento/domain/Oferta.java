package br.com.celke.cursos.pagamento.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;

/**
 * O que se vende: acesso a uma turma por um produto/oferta de um meio de pagamento. A turma é só o id
 * (os dados vêm do módulo {@code curso}).
 */
@Entity
@Table(name = "ofertas")
public class Oferta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "turma_id", nullable = false, updatable = false)
    private Long turmaId;

    @Column(name = "gateway_id", nullable = false, updatable = false)
    private Long gatewayId;

    @Column(name = "produto_externo", nullable = false, length = 100)
    private String produtoExterno;

    @Column(name = "oferta_externa", nullable = false, length = 100)
    private String ofertaExterna;

    /** Código do produto no link do checkout, quando diferente do id do produto. */
    @Column(name = "codigo_checkout", length = 100)
    private String codigoCheckout;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(name = "preco_centavos", nullable = false)
    private long precoCentavos;

    @Column(nullable = false, length = 3)
    private String moeda;

    /** Nulo: acesso sem prazo. */
    @Column(name = "dias_acesso")
    private Integer diasAcesso;

    @Column(nullable = false)
    private boolean ativa;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Oferta() {}

    public Oferta(Long turmaId, Long gatewayId, DadosOferta dados, Instant agora) {
        this.turmaId = turmaId;
        this.gatewayId = gatewayId;
        this.ativa = true;
        this.criadoEm = agora;
        atualizar(dados, agora);
    }

    public void atualizar(DadosOferta dados, Instant agora) {
        this.produtoExterno = dados.produtoExterno().strip();
        this.ofertaExterna = dados.ofertaExterna().strip();
        this.codigoCheckout =
                dados.codigoCheckout() == null || dados.codigoCheckout().isBlank()
                        ? null
                        : dados.codigoCheckout().strip();
        this.titulo = dados.titulo().strip();
        this.precoCentavos = dados.precoCentavos();
        this.moeda = dados.moeda().strip().toUpperCase(Locale.ROOT);
        this.diasAcesso = dados.diasAcesso();
        this.atualizadoEm = agora;
    }

    public void alterarAtiva(boolean ativa, Instant agora) {
        this.ativa = ativa;
        this.atualizadoEm = agora;
    }

    /** Código do produto para o link do checkout (o próprio id do produto, se não informado). */
    public String codigoParaCheckout() {
        return codigoCheckout == null ? produtoExterno : codigoCheckout;
    }

    public Long getId() {
        return id;
    }

    public Long getTurmaId() {
        return turmaId;
    }

    public Long getGatewayId() {
        return gatewayId;
    }

    public String getProdutoExterno() {
        return produtoExterno;
    }

    public String getOfertaExterna() {
        return ofertaExterna;
    }

    public String getCodigoCheckout() {
        return codigoCheckout;
    }

    public String getTitulo() {
        return titulo;
    }

    public long getPrecoCentavos() {
        return precoCentavos;
    }

    public String getMoeda() {
        return moeda;
    }

    public Integer getDiasAcesso() {
        return diasAcesso;
    }

    public boolean isAtiva() {
        return ativa;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    /** Campos editáveis da oferta. */
    public record DadosOferta(
            String produtoExterno,
            String ofertaExterna,
            String codigoCheckout,
            String titulo,
            long precoCentavos,
            String moeda,
            Integer diasAcesso) {}
}
