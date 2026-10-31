package br.com.celke.cursos.pagamento.api;

import br.com.celke.cursos.pagamento.domain.Oferta.DadosOferta;
import br.com.celke.cursos.pagamento.domain.TipoGateway;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** Corpos das rotas de pagamento: checkout do aluno e painel do ADM. */
public final class PagamentoRequests {

    private PagamentoRequests() {}

    public record Checkout(@NotNull Long ofertaId) {}

    /** Campos em {@code valores}, pela chave de {@code camposConfiguracao}; segredo em branco mantém o atual. */
    public record NovoGateway(
            @NotNull TipoGateway tipo,
            @NotBlank @Size(max = 120) String nome,
            Map<String, String> valores) {}

    public record AtualizarGateway(
            @NotBlank @Size(max = 120) String nome, Map<String, String> valores) {}

    /**
     * Oferta de uma turma.
     *
     * @param gatewayId meio de pagamento; sem ele, o ativo (só na criação)
     * @param codigoCheckout código do produto no link do checkout, se diferente do id do produto
     * @param diasAcesso sem ele, acesso sem prazo
     */
    public record Oferta(
            Long gatewayId,
            @NotBlank @Size(max = 100) String produtoExterno,
            @NotBlank @Size(max = 100) String ofertaExterna,
            @Size(max = 100) String codigoCheckout,
            @NotBlank @Size(max = 120) String titulo,
            @NotNull @Min(0) @Max(1_000_000_000) Long precoCentavos,

            @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "use o código de 3 letras (ex.: BRL)")
            String moeda,

            @Min(1) @Max(36_500) Integer diasAcesso) {

        public DadosOferta dados() {
            return new DadosOferta(
                    produtoExterno, ofertaExterna, codigoCheckout, titulo, precoCentavos, moeda, diasAcesso);
        }
    }

    public record StatusOferta(@NotNull Boolean ativa) {}
}
