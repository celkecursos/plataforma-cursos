package br.com.celke.cursos.pagamento.api;

import br.com.celke.cursos.pagamento.api.PagamentoResponses.OfertaResponse;
import br.com.celke.cursos.pagamento.application.OfertaAdmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/adm")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ADM — Ofertas")
public class OfertaAdmController {

    private final OfertaAdmService ofertaAdmService;

    public OfertaAdmController(OfertaAdmService ofertaAdmService) {
        this.ofertaAdmService = ofertaAdmService;
    }

    @GetMapping("/turmas/{turmaId}/ofertas")
    @Operation(summary = "Ofertas da turma", description = "Inclui as inativas.")
    public List<OfertaResponse> listar(@PathVariable Long turmaId) {
        return ofertaAdmService.listar(turmaId).stream().map(OfertaResponse::de).toList();
    }

    @PostMapping("/turmas/{turmaId}/ofertas")
    @Operation(summary = "Criar oferta", description = "Sem gatewayId, no meio de pagamento ativo.")
    public ResponseEntity<OfertaResponse> criar(
            @PathVariable Long turmaId, @Valid @RequestBody PagamentoRequests.Oferta request) {
        OfertaResponse oferta =
                OfertaResponse.de(ofertaAdmService.criar(turmaId, request.gatewayId(), request.dados()));
        return ResponseEntity.created(URI.create("/api/adm/ofertas/" + oferta.id()))
                .body(oferta);
    }

    @PutMapping("/ofertas/{id}")
    @Operation(summary = "Alterar oferta", description = "O meio de pagamento não muda.")
    public OfertaResponse atualizar(@PathVariable Long id, @Valid @RequestBody PagamentoRequests.Oferta request) {
        return OfertaResponse.de(ofertaAdmService.atualizar(id, request.dados()));
    }

    @PatchMapping("/ofertas/{id}/status")
    @Operation(summary = "Ativar ou desativar oferta", description = "Inativa some do site e não abre checkout.")
    public OfertaResponse alterarStatus(
            @PathVariable Long id, @Valid @RequestBody PagamentoRequests.StatusOferta request) {
        return OfertaResponse.de(ofertaAdmService.alterarAtiva(id, request.ativa()));
    }
}
