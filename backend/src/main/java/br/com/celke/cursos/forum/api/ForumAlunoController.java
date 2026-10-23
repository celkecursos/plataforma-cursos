package br.com.celke.cursos.forum.api;

import br.com.celke.cursos.forum.api.ForumResponses.EnvioAnexoResponse;
import br.com.celke.cursos.forum.api.ForumResponses.TopicoResponse;
import br.com.celke.cursos.forum.api.ForumResponses.TopicoResumoResponse;
import br.com.celke.cursos.forum.application.AnexoForumService;
import br.com.celke.cursos.forum.application.ForumAlunoService;
import br.com.celke.cursos.shared.web.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Fórum na área do aluno, sempre no escopo do usuário do token e com matrícula vigente na turma. */
@RestController
@RequestMapping("/api/aluno")
@Tag(name = "Área do aluno — Fórum")
public class ForumAlunoController {

    private final ForumAlunoService forumAlunoService;
    private final AnexoForumService anexoForumService;

    public ForumAlunoController(ForumAlunoService forumAlunoService, AnexoForumService anexoForumService) {
        this.forumAlunoService = forumAlunoService;
        this.anexoForumService = anexoForumService;
    }

    @GetMapping("/turmas/{turmaId}/forum")
    @Operation(summary = "Tópicos da turma", description = "Fixados primeiro; filtro opcional pela aula.")
    public Pagina<TopicoResumoResponse> listar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long turmaId,
            @RequestParam(required = false) Long aulaId,
            @ParameterObject Pageable pagina) {
        return Pagina.de(
                forumAlunoService.listar(usuario(jwt), turmaId, aulaId, pagina).map(TopicoResumoResponse::aluno));
    }

    @PostMapping("/turmas/{turmaId}/forum")
    @Operation(
            summary = "Perguntar",
            description = "Corpo em Markdown (o HTML é filtrado pela API); imagens anexadas pelas chaves enviadas.")
    public ResponseEntity<TopicoResponse> perguntar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long turmaId,
            @Valid @RequestBody ForumRequests.Perguntar request) {
        TopicoResponse topico = TopicoResponse.aluno(forumAlunoService.perguntar(
                usuario(jwt), turmaId, request.aulaId(), request.titulo(), request.corpo(), request.anexos()));
        return ResponseEntity.created(URI.create("/api/aluno/forum/topicos/" + topico.id()))
                .body(topico);
    }

    @GetMapping("/forum/topicos/{topicoId}")
    @Operation(summary = "Tópico com as respostas")
    public TopicoResponse topico(@AuthenticationPrincipal Jwt jwt, @PathVariable Long topicoId) {
        return TopicoResponse.aluno(forumAlunoService.topico(usuario(jwt), topicoId));
    }

    @PostMapping("/forum/topicos/{topicoId}/respostas")
    @Operation(summary = "Responder", description = "Corpo em Markdown; devolve o tópico atualizado.")
    public TopicoResponse responder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long topicoId,
            @Valid @RequestBody ForumRequests.Responder request) {
        return TopicoResponse.aluno(
                forumAlunoService.responder(usuario(jwt), topicoId, request.corpo(), request.anexos()));
    }

    @PostMapping("/forum/anexos/envio")
    @Operation(
            summary = "Iniciar o envio de uma imagem",
            description = "JPEG ou PNG até 5 MB. Devolve a URL para enviar direto ao armazenamento e a chave para"
                    + " anexar na pergunta ou resposta.")
    public EnvioAnexoResponse iniciarEnvioAnexo(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ForumRequests.IniciarEnvioAnexo request) {
        return EnvioAnexoResponse.de(
                anexoForumService.iniciarEnvio(usuario(jwt), request.contentType(), request.tamanho()));
    }

    private static Long usuario(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
