package br.com.celke.cursos.pagamento.api;

import br.com.celke.cursos.pagamento.api.PagamentoResponses.EventoDetalhe;
import br.com.celke.cursos.pagamento.api.PagamentoResponses.EventoResumo;
import br.com.celke.cursos.pagamento.api.PagamentoResponses.PedidoAdm;
import br.com.celke.cursos.pagamento.api.PagamentoResponses.PedidoDetalhe;
import br.com.celke.cursos.pagamento.application.EventoAdmService;
import br.com.celke.cursos.pagamento.application.PedidoAdmService;
import br.com.celke.cursos.pagamento.application.PedidoAdmService.FiltroPedidos;
import br.com.celke.cursos.pagamento.domain.StatusEvento;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.shared.web.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/adm/pagamentos")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ADM — Pedidos e eventos")
public class PedidoAdmController {

    private final PedidoAdmService pedidoAdmService;
    private final EventoAdmService eventoAdmService;

    public PedidoAdmController(PedidoAdmService pedidoAdmService, EventoAdmService eventoAdmService) {
        this.pedidoAdmService = pedidoAdmService;
        this.eventoAdmService = eventoAdmService;
    }

    @GetMapping("/pedidos")
    @Operation(
            summary = "Pedidos",
            description = "Mais recentes primeiro. Filtros: status, gateway, trecho do e-mail e período de criação"
                    + " (de inclusivo, até exclusivo; ISO-8601).")
    public Pagina<PedidoAdm> pedidos(
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) Long gatewayId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Instant de,
            @RequestParam(required = false) Instant ate,
            @ParameterObject Pageable pagina) {
        return Pagina.de(pedidoAdmService
                .listar(new FiltroPedidos(status, gatewayId, email, de, ate), pagina)
                .map(PedidoAdm::de));
    }

    @GetMapping("/pedidos/{id}")
    @Operation(summary = "Pedido", description = "Com a linha do tempo e os eventos recebidos.")
    public PedidoDetalhe pedido(@PathVariable Long id) {
        return PedidoDetalhe.de(pedidoAdmService.detalhar(id));
    }

    @GetMapping("/eventos")
    @Operation(summary = "Eventos recebidos", description = "Mais recentes primeiro; filtro opcional por status.")
    public Pagina<EventoResumo> eventos(
            @RequestParam(required = false) StatusEvento status, @ParameterObject Pageable pagina) {
        return Pagina.de(eventoAdmService.listar(status, pagina).map(EventoResumo::de));
    }

    @GetMapping("/eventos/{id}")
    @Operation(summary = "Evento", description = "Com o corpo recebido (documento e telefone mascarados).")
    public EventoDetalhe evento(@PathVariable Long id) {
        return EventoDetalhe.de(eventoAdmService.buscar(id));
    }

    @PostMapping("/eventos/{id}/reprocessamento")
    @Operation(summary = "Reprocessar evento", description = "Só eventos em ERRO ou IGNORADO.")
    public EventoResumo reprocessar(@PathVariable Long id) {
        return EventoResumo.de(eventoAdmService.reprocessar(id));
    }
}
