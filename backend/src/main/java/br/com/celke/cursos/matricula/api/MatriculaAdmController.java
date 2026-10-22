package br.com.celke.cursos.matricula.api;

import br.com.celke.cursos.matricula.api.MatriculaResponses.MatriculaAdm;
import br.com.celke.cursos.matricula.application.MatriculaAdmService;
import br.com.celke.cursos.shared.web.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/adm")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ADM — Matrículas")
public class MatriculaAdmController {

    private final MatriculaAdmService matriculaAdmService;
    private final Clock clock;

    public MatriculaAdmController(MatriculaAdmService matriculaAdmService, Clock clock) {
        this.matriculaAdmService = matriculaAdmService;
        this.clock = clock;
    }

    @GetMapping("/turmas/{turmaId}/matriculas")
    @Operation(summary = "Listar as matrículas da turma", description = "Inclui as canceladas; mais recentes primeiro.")
    public Pagina<MatriculaAdm> listar(@PathVariable Long turmaId, @ParameterObject Pageable pagina) {
        return Pagina.de(
                matriculaAdmService.listar(turmaId, pagina).map(item -> MatriculaAdm.de(item, clock.instant())));
    }

    @PostMapping("/turmas/{turmaId}/matriculas")
    @Operation(
            summary = "Matricular",
            description = "Pelo e-mail de um usuário existente; sem início, começa agora; sem fim, não vence.")
    public ResponseEntity<MatriculaAdm> matricular(
            @PathVariable Long turmaId, @Valid @RequestBody MatriculaRequests.Matricular request) {
        MatriculaAdm matricula =
                MatriculaAdm.de(matriculaAdmService.matricular(turmaId, request.dados()), clock.instant());
        return ResponseEntity.created(URI.create("/api/adm/matriculas/" + matricula.id()))
                .body(matricula);
    }

    @PutMapping("/matriculas/{id}/periodo")
    @Operation(summary = "Alterar o período da matrícula")
    public MatriculaAdm alterarPeriodo(@PathVariable Long id, @Valid @RequestBody MatriculaRequests.Periodo request) {
        return MatriculaAdm.de(
                matriculaAdmService.alterarPeriodo(id, request.inicio(), request.fim()), clock.instant());
    }

    @PostMapping("/matriculas/{id}/cancelamento")
    @Operation(
            summary = "Cancelar a matrícula",
            description = "O acesso termina na hora; o registro fica no histórico.")
    public MatriculaAdm cancelar(@PathVariable Long id) {
        return MatriculaAdm.de(matriculaAdmService.cancelar(id), clock.instant());
    }
}
