package br.com.celke.cursos.forum.api;

import br.com.celke.cursos.forum.api.ForumResponses.TopicoResponse;
import br.com.celke.cursos.forum.api.ForumResponses.TopicoResumoResponse;
import br.com.celke.cursos.forum.application.ForumAdmService;
import br.com.celke.cursos.shared.web.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/adm")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ADM — Fórum")
public class ForumAdmController {

    private final ForumAdmService forumAdmService;

    public ForumAdmController(ForumAdmService forumAdmService) {
        this.forumAdmService = forumAdmService;
    }

    @GetMapping("/turmas/{turmaId}/forum")
    @Operation(summary = "Tópicos da turma", description = "Inclui os ocultos, para moderação.")
    public Pagina<TopicoResumoResponse> listar(@PathVariable Long turmaId, @ParameterObject Pageable pagina) {
        return Pagina.de(forumAdmService.listar(turmaId, pagina).map(TopicoResumoResponse::moderacao));
    }

    @GetMapping("/forum/topicos/{topicoId}")
    @Operation(summary = "Tópico com todas as respostas", description = "Inclui as ocultas.")
    public TopicoResponse topico(@PathVariable Long topicoId) {
        return TopicoResponse.moderacao(forumAdmService.topico(topicoId));
    }

    @PatchMapping("/forum/topicos/{topicoId}")
    @Operation(summary = "Moderar tópico", description = "Ocultar/mostrar e fixar/soltar.")
    public TopicoResponse moderarTopico(
            @PathVariable Long topicoId, @Valid @RequestBody ForumRequests.ModerarTopico request) {
        return TopicoResponse.moderacao(forumAdmService.moderarTopico(topicoId, request.oculto(), request.fixado()));
    }

    @PatchMapping("/forum/respostas/{respostaId}")
    @Operation(summary = "Moderar resposta", description = "Ocultar ou mostrar.")
    public TopicoResponse moderarResposta(
            @PathVariable Long respostaId, @Valid @RequestBody ForumRequests.ModerarResposta request) {
        return TopicoResponse.moderacao(forumAdmService.moderarResposta(respostaId, request.oculto()));
    }

    @PostMapping("/forum/topicos/{topicoId}/respostas")
    @Operation(summary = "Responder como instrutor", description = "O tópico fica marcado como respondido.")
    public TopicoResponse responder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long topicoId,
            @Valid @RequestBody ForumRequests.ResponderInstrutor request) {
        return TopicoResponse.moderacao(
                forumAdmService.responder(Long.valueOf(jwt.getSubject()), topicoId, request.corpo()));
    }
}
