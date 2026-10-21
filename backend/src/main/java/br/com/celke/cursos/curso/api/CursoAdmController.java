package br.com.celke.cursos.curso.api;

import br.com.celke.cursos.curso.api.CursoResponses.CursoAdm;
import br.com.celke.cursos.curso.api.CursoResponses.EnvioCapaResponse;
import br.com.celke.cursos.curso.application.CapaCursoService;
import br.com.celke.cursos.curso.application.CursoAdmService;
import br.com.celke.cursos.curso.domain.Curso;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.shared.web.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/adm/cursos")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ADM — Cursos")
public class CursoAdmController {

    private final CursoAdmService cursoAdmService;
    private final CapaCursoService capaCursoService;

    public CursoAdmController(CursoAdmService cursoAdmService, CapaCursoService capaCursoService) {
        this.cursoAdmService = cursoAdmService;
        this.capaCursoService = capaCursoService;
    }

    @GetMapping
    @Operation(summary = "Listar cursos", description = "Em ordem alfabética; filtro opcional por status.")
    public Pagina<CursoAdm> listar(
            @RequestParam(required = false) StatusCurso status, @ParameterObject Pageable pagina) {
        return Pagina.de(cursoAdmService.listar(status, pagina).map(this::resposta));
    }

    @PostMapping
    @Operation(summary = "Criar curso", description = "Nasce em rascunho; sem slug, ele é gerado a partir do título.")
    public ResponseEntity<CursoAdm> criar(@Valid @RequestBody CursoRequests.Curso request) {
        Curso curso = cursoAdmService.criar(request.dados());
        return ResponseEntity.created(URI.create("/api/adm/cursos/" + curso.getId()))
                .body(resposta(curso));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar curso")
    public CursoAdm buscar(@PathVariable Long id) {
        return resposta(cursoAdmService.buscar(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar curso")
    public CursoAdm atualizar(@PathVariable Long id, @Valid @RequestBody CursoRequests.Curso request) {
        return resposta(cursoAdmService.atualizar(id, request.dados()));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Publicar, arquivar ou voltar para rascunho")
    public CursoAdm alterarStatus(@PathVariable Long id, @Valid @RequestBody CursoRequests.AlterarStatusCurso request) {
        return resposta(cursoAdmService.alterarStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir curso", description = "Só sem turmas; curso com turmas é arquivado.")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        capaCursoService.excluirCurso(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/capa/envio")
    @Operation(
            summary = "Iniciar o envio da capa",
            description = "Devolve a URL para enviar a imagem (JPEG ou PNG, até 5 MB) direto ao armazenamento.")
    public EnvioCapaResponse iniciarEnvioCapa(
            @PathVariable Long id, @Valid @RequestBody CursoRequests.IniciarEnvioCapa request) {
        return EnvioCapaResponse.de(capaCursoService.iniciarEnvio(id, request.contentType(), request.tamanho()));
    }

    @PutMapping("/{id}/capa")
    @Operation(
            summary = "Confirmar a capa enviada",
            description = "Confere a imagem, reduz para até 1280 × 720 e regrava em JPEG sem metadados.")
    public CursoAdm confirmarCapa(@PathVariable Long id, @Valid @RequestBody CursoRequests.ConfirmarCapa request) {
        return resposta(capaCursoService.confirmar(id, request.chave()));
    }

    @DeleteMapping("/{id}/capa")
    @Operation(summary = "Remover a capa")
    public ResponseEntity<Void> removerCapa(@PathVariable Long id) {
        capaCursoService.remover(id);
        return ResponseEntity.noContent().build();
    }

    private CursoAdm resposta(Curso curso) {
        return CursoAdm.de(curso, capaCursoService.url(curso));
    }
}
