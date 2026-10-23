package br.com.celke.cursos.auditoria.api;

import br.com.celke.cursos.auditoria.api.AuditoriaResponses.Registro;
import br.com.celke.cursos.auditoria.application.AuditoriaService;
import br.com.celke.cursos.auditoria.application.AuditoriaService.FiltroAuditoria;
import br.com.celke.cursos.shared.auditoria.OperacaoAuditoria;
import br.com.celke.cursos.shared.web.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/adm/auditoria")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ADM — Auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    @Operation(
            summary = "Trilha de auditoria",
            description = "Mais recentes primeiro. Filtros: usuário (id ou e-mail), entidade e id, operação e"
                    + " período (de inclusivo, até exclusivo; ISO-8601).")
    public Pagina<Registro> listar(
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String entidade,
            @RequestParam(required = false) String entidadeId,
            @RequestParam(required = false) OperacaoAuditoria operacao,
            @RequestParam(required = false) Instant de,
            @RequestParam(required = false) Instant ate,
            @ParameterObject Pageable pagina) {
        return Pagina.de(auditoriaService
                .listar(new FiltroAuditoria(usuarioId, email, entidade, entidadeId, operacao, de, ate), pagina)
                .map(Registro::de));
    }

    @GetMapping("/entidades")
    @Operation(summary = "Entidades que aparecem na trilha", description = "Para o filtro da tela.")
    public List<String> entidades() {
        return auditoriaService.entidades();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Registro da trilha")
    public Registro buscar(@PathVariable Long id) {
        return Registro.de(auditoriaService.buscar(id));
    }
}
