package br.com.celke.cursos.pagamento.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.pagamento.domain.TipoGateway;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PagamentoRequestsTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> camposInvalidos(Object request) {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static PagamentoRequests.Oferta ofertaValida() {
        return new PagamentoRequests.Oferta(null, "4774438", "k2pasun0", null, "À vista", 19_700L, "BRL", 365);
    }

    @Test
    @DisplayName("oferta válida: gateway, código do checkout e dias de acesso são opcionais")
    void ofertaValidaPassa() {
        assertThat(camposInvalidos(ofertaValida())).isEmpty();
        assertThat(camposInvalidos(new PagamentoRequests.Oferta(1L, "p", "o", "c", "t", 0L, "usd", null)))
                .isEmpty();
    }

    @Test
    @DisplayName("oferta: obrigatórios, tamanhos, preço, moeda e dias de acesso")
    void ofertaInvalida() {
        assertThat(camposInvalidos(new PagamentoRequests.Oferta(null, " ", "", "x".repeat(101), null, null, "R$", 0)))
                .containsExactlyInAnyOrder(
                        "produtoExterno",
                        "ofertaExterna",
                        "codigoCheckout",
                        "titulo",
                        "precoCentavos",
                        "moeda",
                        "diasAcesso");
        assertThat(camposInvalidos(new PagamentoRequests.Oferta(
                        null, "x".repeat(101), "x".repeat(101), null, "x".repeat(121), -1L, "BRLL", 36_501)))
                .containsExactlyInAnyOrder(
                        "produtoExterno", "ofertaExterna", "titulo", "precoCentavos", "moeda", "diasAcesso");
        assertThat(camposInvalidos(
                        new PagamentoRequests.Oferta(null, "p", "o", null, "t", 1_000_000_001L, "BRL", null)))
                .containsExactly("precoCentavos");
    }

    @Test
    @DisplayName("os dados da oferta repassam todos os campos")
    void dados() {
        var dados = ofertaValida().dados();

        assertThat(dados.produtoExterno()).isEqualTo("4774438");
        assertThat(dados.ofertaExterna()).isEqualTo("k2pasun0");
        assertThat(dados.titulo()).isEqualTo("À vista");
        assertThat(dados.precoCentavos()).isEqualTo(19_700L);
        assertThat(dados.moeda()).isEqualTo("BRL");
        assertThat(dados.diasAcesso()).isEqualTo(365);
    }

    @Test
    @DisplayName("gateway exige tipo e nome; checkout exige a oferta; status exige o valor")
    void demais() {
        assertThat(camposInvalidos(new PagamentoRequests.NovoGateway(TipoGateway.HOTMART, "Hotmart", Map.of())))
                .isEmpty();
        assertThat(camposInvalidos(new PagamentoRequests.NovoGateway(null, "x".repeat(121), null)))
                .containsExactlyInAnyOrder("tipo", "nome");
        assertThat(camposInvalidos(new PagamentoRequests.AtualizarGateway(" ", null)))
                .containsExactly("nome");
        assertThat(camposInvalidos(new PagamentoRequests.Checkout(null))).containsExactly("ofertaId");
        assertThat(camposInvalidos(new PagamentoRequests.StatusOferta(null))).containsExactly("ativa");
    }
}
