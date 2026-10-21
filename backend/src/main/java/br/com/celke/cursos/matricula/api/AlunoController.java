package br.com.celke.cursos.matricula.api;

import br.com.celke.cursos.matricula.api.MatriculaResponses.AulaAlunoResponse;
import br.com.celke.cursos.matricula.api.MatriculaResponses.MeuCursoResponse;
import br.com.celke.cursos.matricula.api.MatriculaResponses.TurmaAlunoResponse;
import br.com.celke.cursos.matricula.application.AcessoAulaService;
import br.com.celke.cursos.matricula.application.AlunoCursosService;
import br.com.celke.cursos.matricula.domain.FiltroMeusCursos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Área do aluno: tudo no escopo do usuário do token (o id nunca vem do cliente). */
@RestController
@RequestMapping("/api/aluno")
@Tag(name = "Área do aluno")
public class AlunoController {

    private final AlunoCursosService alunoCursosService;
    private final AcessoAulaService acessoAulaService;

    public AlunoController(AlunoCursosService alunoCursosService, AcessoAulaService acessoAulaService) {
        this.alunoCursosService = alunoCursosService;
        this.acessoAulaService = acessoAulaService;
    }

    @GetMapping("/cursos")
    @Operation(summary = "Meus cursos", description = "Aba ATIVOS (padrão), ARQUIVADOS ou EXPIRADOS.")
    public List<MeuCursoResponse> meusCursos(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "ATIVOS") FiltroMeusCursos situacao) {
        return alunoCursosService.meusCursos(usuario(jwt), situacao).stream()
                .map(MeuCursoResponse::de)
                .toList();
    }

    @GetMapping("/turmas/{turmaId}")
    @Operation(summary = "Turma com módulos e aulas", description = "Exige matrícula vigente.")
    public TurmaAlunoResponse turma(@AuthenticationPrincipal Jwt jwt, @PathVariable Long turmaId) {
        return TurmaAlunoResponse.de(alunoCursosService.turma(usuario(jwt), turmaId));
    }

    @GetMapping("/aulas/{aulaId}")
    @Operation(summary = "Aula", description = "Exige matrícula vigente, exceto aula gratuita de curso publicado.")
    public AulaAlunoResponse aula(@AuthenticationPrincipal Jwt jwt, @PathVariable Long aulaId) {
        return AulaAlunoResponse.de(acessoAulaService.exigirAcesso(usuario(jwt), aulaId));
    }

    @PutMapping("/matriculas/{id}/arquivamento")
    @Operation(summary = "Arquivar curso", description = "Tira o curso da aba de ativos; o acesso não muda.")
    public ResponseEntity<Void> arquivar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        alunoCursosService.arquivar(usuario(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/matriculas/{id}/arquivamento")
    @Operation(summary = "Desarquivar curso")
    public ResponseEntity<Void> desarquivar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        alunoCursosService.desarquivar(usuario(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private static Long usuario(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
