package br.com.celke.cursos.curso.api;

import br.com.celke.cursos.curso.api.CursoResponses.Modulo;
import br.com.celke.cursos.curso.application.ModuloAdmService;
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
@Tag(name = "ADM — Módulos")
public class ModuloAdmController {

    private final ModuloAdmService moduloAdmService;

    public ModuloAdmController(ModuloAdmService moduloAdmService) {
        this.moduloAdmService = moduloAdmService;
    }

    @GetMapping("/turmas/{turmaId}/modulos")
    @Operation(summary = "Listar os módulos da turma", description = "Na ordem do curso.")
    public List<Modulo> listar(@PathVariable Long turmaId) {
        return moduloAdmService.listar(turmaId).stream().map(Modulo::de).toList();
    }

    @PostMapping("/turmas/{turmaId}/modulos")
    @Operation(summary = "Criar módulo", description = "Entra no fim da turma.")
    public ResponseEntity<Modulo> criar(@PathVariable Long turmaId, @Valid @RequestBody CursoRequests.Modulo request) {
        Modulo modulo = Modulo.de(moduloAdmService.criar(turmaId, request.titulo()));
        return ResponseEntity.created(URI.create("/api/adm/modulos/" + modulo.id()))
                .body(modulo);
    }

    @PutMapping("/turmas/{turmaId}/modulos/ordem")
    @Operation(summary = "Reordenar os módulos", description = "Recebe os ids de todos os módulos da turma.")
    public List<Modulo> reordenar(@PathVariable Long turmaId, @Valid @RequestBody CursoRequests.Ordem request) {
        return moduloAdmService.reordenar(turmaId, request.ids()).stream()
                .map(Modulo::de)
                .toList();
    }

    @PutMapping("/modulos/{id}")
    @Operation(summary = "Renomear módulo")
    public Modulo renomear(@PathVariable Long id, @Valid @RequestBody CursoRequests.Modulo request) {
        return Modulo.de(moduloAdmService.renomear(id, request.titulo()));
    }

    @PatchMapping("/modulos/{id}/status")
    @Operation(summary = "Ativar ou inativar módulo")
    public Modulo alterarStatus(
            @PathVariable Long id, @Valid @RequestBody CursoRequests.AlterarStatusConteudo request) {
        return Modulo.de(moduloAdmService.alterarStatus(id, request.status()));
    }

    @DeleteMapping("/modulos/{id}")
    @Operation(summary = "Excluir módulo", description = "Só sem aulas.")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        moduloAdmService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
