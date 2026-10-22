package br.com.celke.cursos.progresso.api;

import br.com.celke.cursos.progresso.application.ProgressoService;
import br.com.celke.cursos.progresso.application.ProgressoService.Progresso;
import br.com.celke.cursos.progresso.application.ProgressoService.ProgressoTurma;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Progresso do aluno, sempre do usuário do token (o id nunca vem do cliente). */
@RestController
@RequestMapping("/api/aluno")
@Tag(name = "Área do aluno — Progresso")
public class ProgressoController {

    private final ProgressoService progressoService;

    public ProgressoController(ProgressoService progressoService) {
        this.progressoService = progressoService;
    }

    @GetMapping("/aulas/{aulaId}/progresso")
    @Operation(summary = "Progresso na aula", description = "Posição no vídeo, vista e revisar; zerado se nunca abriu.")
    public Progresso daAula(@AuthenticationPrincipal Jwt jwt, @PathVariable Long aulaId) {
        return progressoService.daAula(usuario(jwt), aulaId);
    }

    @PatchMapping("/aulas/{aulaId}/progresso")
    @Operation(
            summary = "Atualizar o progresso na aula",
            description = "Só os campos enviados mudam (posição a cada poucos segundos, vista, revisar).")
    public Progresso atualizar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long aulaId,
            @Valid @RequestBody ProgressoRequests.Atualizar request) {
        return progressoService.atualizar(
                usuario(jwt), aulaId, request.posicaoSegundos(), request.vista(), request.revisar());
    }

    @GetMapping("/turmas/{turmaId}/progresso")
    @Operation(
            summary = "Progresso na turma",
            description = "Percentual de aulas vistas e o progresso de cada aula ativa. Exige matrícula vigente.")
    public ProgressoTurma daTurma(@AuthenticationPrincipal Jwt jwt, @PathVariable Long turmaId) {
        return progressoService.daTurma(usuario(jwt), turmaId);
    }

    private static Long usuario(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
