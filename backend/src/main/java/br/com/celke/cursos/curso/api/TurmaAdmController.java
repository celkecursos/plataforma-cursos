package br.com.celke.cursos.curso.api;

import br.com.celke.cursos.curso.api.CursoResponses.Turma;
import br.com.celke.cursos.curso.application.TurmaAdmService;
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
@Tag(name = "ADM — Turmas")
public class TurmaAdmController {

    private final TurmaAdmService turmaAdmService;

    public TurmaAdmController(TurmaAdmService turmaAdmService) {
        this.turmaAdmService = turmaAdmService;
    }

    @GetMapping("/cursos/{cursoId}/turmas")
    @Operation(summary = "Listar as turmas do curso")
    public List<Turma> listar(@PathVariable Long cursoId) {
        return turmaAdmService.listar(cursoId).stream().map(Turma::de).toList();
    }

    @PostMapping("/cursos/{cursoId}/turmas")
    @Operation(summary = "Criar turma", description = "Nasce em preparação.")
    public ResponseEntity<Turma> criar(@PathVariable Long cursoId, @Valid @RequestBody CursoRequests.Turma request) {
        Turma turma = Turma.de(turmaAdmService.criar(cursoId, request.nome()));
        return ResponseEntity.created(URI.create("/api/adm/turmas/" + turma.id()))
                .body(turma);
    }

    @PutMapping("/turmas/{id}")
    @Operation(summary = "Renomear turma")
    public Turma renomear(@PathVariable Long id, @Valid @RequestBody CursoRequests.Turma request) {
        return Turma.de(turmaAdmService.renomear(id, request.nome()));
    }

    @PatchMapping("/turmas/{id}/status")
    @Operation(summary = "Ativar ou encerrar turma", description = "O catálogo mostra a turma ativa mais recente.")
    public Turma alterarStatus(@PathVariable Long id, @Valid @RequestBody CursoRequests.AlterarStatusTurma request) {
        return Turma.de(turmaAdmService.alterarStatus(id, request.status()));
    }

    @PutMapping("/turmas/{id}/certificado")
    @Operation(
            summary = "Regra do certificado",
            description = "Percentual de aulas vistas que dá direito ao certificado (1 a 100) e carga horária.")
    public Turma definirCertificado(@PathVariable Long id, @Valid @RequestBody CursoRequests.CertificadoTurma request) {
        return Turma.de(
                turmaAdmService.definirCertificado(id, request.percentualConclusao(), request.cargaHorariaHoras()));
    }

    @DeleteMapping("/turmas/{id}")
    @Operation(summary = "Excluir turma", description = "Só sem módulos; turma com conteúdo é encerrada.")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        turmaAdmService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
