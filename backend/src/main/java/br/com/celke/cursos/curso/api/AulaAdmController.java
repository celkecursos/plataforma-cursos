package br.com.celke.cursos.curso.api;

import br.com.celke.cursos.curso.api.CursoResponses.Aula;
import br.com.celke.cursos.curso.application.AulaAdmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@Tag(name = "ADM — Aulas")
public class AulaAdmController {

    private final AulaAdmService aulaAdmService;

    public AulaAdmController(AulaAdmService aulaAdmService) {
        this.aulaAdmService = aulaAdmService;
    }

    @GetMapping("/modulos/{moduloId}/aulas")
    @Operation(summary = "Listar as aulas do módulo", description = "Na ordem do curso.")
    public List<Aula> listar(@PathVariable Long moduloId) {
        return aulaAdmService.listar(moduloId).stream().map(Aula::de).toList();
    }

    @PostMapping("/modulos/{moduloId}/aulas")
    @Operation(summary = "Criar aula", description = "Entra no fim do módulo.")
    public ResponseEntity<Aula> criar(@PathVariable Long moduloId, @Valid @RequestBody CursoRequests.Aula request) {
        Aula aula = Aula.de(aulaAdmService.criar(moduloId, request.dados()));
        return ResponseEntity.created(URI.create("/api/adm/aulas/" + aula.id())).body(aula);
    }

    @PutMapping("/modulos/{moduloId}/aulas/ordem")
    @Operation(summary = "Reordenar as aulas", description = "Recebe os ids de todas as aulas do módulo.")
    public List<Aula> reordenar(@PathVariable Long moduloId, @Valid @RequestBody CursoRequests.Ordem request) {
        return aulaAdmService.reordenar(moduloId, request.ids()).stream()
                .map(Aula::de)
                .toList();
    }

    @PutMapping("/aulas/{id}")
    @Operation(summary = "Atualizar aula")
    public Aula atualizar(@PathVariable Long id, @Valid @RequestBody CursoRequests.Aula request) {
        return Aula.de(aulaAdmService.atualizar(id, request.dados()));
    }

    @PatchMapping("/aulas/{id}/status")
    @Operation(summary = "Ativar ou inativar aula")
    public Aula alterarStatus(@PathVariable Long id, @Valid @RequestBody CursoRequests.AlterarStatusConteudo request) {
        return Aula.de(aulaAdmService.alterarStatus(id, request.status()));
    }

    @DeleteMapping("/aulas/{id}")
    @Operation(summary = "Excluir aula")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        aulaAdmService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
