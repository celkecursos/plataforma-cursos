package br.com.celke.cursos.pagamento.api;

import br.com.celke.cursos.pagamento.api.PagamentoResponses.GatewayResponse;
import br.com.celke.cursos.pagamento.application.GatewayAdmService;
import br.com.celke.cursos.pagamento.application.GatewayAdmService.TiposDisponiveis;
import br.com.celke.cursos.pagamento.domain.ResultadoTeste;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/adm/pagamentos")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ADM — Meios de pagamento")
public class GatewayAdmController {

    private final GatewayAdmService gatewayAdmService;

    public GatewayAdmController(GatewayAdmService gatewayAdmService) {
        this.gatewayAdmService = gatewayAdmService;
    }

    @GetMapping("/tipos")
    @Operation(
            summary = "Meios de pagamento disponíveis",
            description = "Com os campos de configuração de cada um; cifraDisponivel falso sem a PAGAMENTOS_CHAVE.")
    public TiposDisponiveis tipos() {
        return gatewayAdmService.tipos();
    }

    @GetMapping("/gateways")
    @Operation(summary = "Meios de pagamento configurados")
    public List<GatewayResponse> listar() {
        return gatewayAdmService.listar().stream().map(GatewayResponse::de).toList();
    }

    @GetMapping("/gateways/{id}")
    @Operation(summary = "Meio de pagamento")
    public GatewayResponse buscar(@PathVariable Long id) {
        return GatewayResponse.de(gatewayAdmService.buscar(id));
    }

    @PostMapping("/gateways")
    @Operation(summary = "Configurar meio de pagamento", description = "Nasce inativo.")
    public ResponseEntity<GatewayResponse> criar(@Valid @RequestBody PagamentoRequests.NovoGateway request) {
        GatewayResponse gateway =
                GatewayResponse.de(gatewayAdmService.criar(request.tipo(), request.nome(), request.valores()));
        return ResponseEntity.created(URI.create("/api/adm/pagamentos/gateways/" + gateway.id()))
                .body(gateway);
    }

    @PutMapping("/gateways/{id}")
    @Operation(summary = "Alterar configuração", description = "Segredo em branco mantém o valor atual.")
    public GatewayResponse atualizar(
            @PathVariable Long id, @Valid @RequestBody PagamentoRequests.AtualizarGateway request) {
        return GatewayResponse.de(gatewayAdmService.atualizar(id, request.nome(), request.valores()));
    }

    @PostMapping("/gateways/{id}/teste")
    @Operation(summary = "Testar as credenciais gravadas com o meio de pagamento")
    public ResultadoTeste testar(@PathVariable Long id) {
        return gatewayAdmService.testar(id);
    }

    @PostMapping("/gateways/{id}/ativacao")
    @Operation(
            summary = "Ativar",
            description = "Passa a ser o meio dos novos checkouts; o anterior continua recebendo notificações.")
    public GatewayResponse ativar(@PathVariable Long id) {
        return GatewayResponse.de(gatewayAdmService.ativar(id));
    }

    @DeleteMapping("/gateways/{id}/ativacao")
    @Operation(summary = "Desativar", description = "Os botões de compra somem; as notificações continuam chegando.")
    public GatewayResponse desativar(@PathVariable Long id) {
        return GatewayResponse.de(gatewayAdmService.desativar(id));
    }
}
