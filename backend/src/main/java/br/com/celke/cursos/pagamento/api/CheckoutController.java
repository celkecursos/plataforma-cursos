package br.com.celke.cursos.pagamento.api;

import br.com.celke.cursos.pagamento.api.PagamentoResponses.CheckoutResponse;
import br.com.celke.cursos.pagamento.api.PagamentoResponses.PedidoPublico;
import br.com.celke.cursos.pagamento.application.CheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Compra: o checkout (aluno logado) e a situação do pedido para a página de retorno (pública). */
@RestController
@RequestMapping("/api/pagamentos")
@Tag(name = "Pagamentos — compra")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping("/checkout")
    @Operation(
            summary = "Comprar uma oferta",
            description = "Abre o pedido com o e-mail da conta e devolve para onde levar o aluno.")
    public ResponseEntity<CheckoutResponse> checkout(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PagamentoRequests.Checkout request) {
        return ResponseEntity.status(201)
                .body(CheckoutResponse.de(checkoutService.iniciar(Long.valueOf(jwt.getSubject()), request.ofertaId())));
    }

    @GetMapping("/pedidos/{codigo}")
    @Operation(
            summary = "Situação do pedido",
            description = "Curso e turma só para o dono logado; para os outros, só o status.")
    public PedidoPublico pedido(@AuthenticationPrincipal Jwt jwt, @PathVariable String codigo) {
        Long usuarioId = jwt == null ? null : Long.valueOf(jwt.getSubject());
        return PedidoPublico.de(checkoutService.situacao(codigo, usuarioId));
    }
}
