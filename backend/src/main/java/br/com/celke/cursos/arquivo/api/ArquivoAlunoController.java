package br.com.celke.cursos.arquivo.api;

import br.com.celke.cursos.arquivo.api.ArquivoResponses.Arquivo;
import br.com.celke.cursos.arquivo.api.ArquivoResponses.DownloadResponse;
import br.com.celke.cursos.arquivo.application.ArquivoAlunoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Arquivos de apoio na área do aluno, sempre no escopo do usuário do token. */
@RestController
@RequestMapping("/api/aluno")
@Tag(name = "Área do aluno — Arquivos de apoio")
public class ArquivoAlunoController {

    private final ArquivoAlunoService arquivoAlunoService;

    public ArquivoAlunoController(ArquivoAlunoService arquivoAlunoService) {
        this.arquivoAlunoService = arquivoAlunoService;
    }

    @GetMapping("/aulas/{aulaId}/arquivos")
    @Operation(summary = "Arquivos da aula", description = "Mesmo acesso da aula (aula gratuita inclusive).")
    public List<Arquivo> daAula(@AuthenticationPrincipal Jwt jwt, @PathVariable Long aulaId) {
        return arquivoAlunoService.daAula(usuario(jwt), aulaId).stream()
                .map(Arquivo::de)
                .toList();
    }

    @GetMapping("/modulos/{moduloId}/arquivos")
    @Operation(summary = "Arquivos do módulo", description = "Exige matrícula vigente na turma.")
    public List<Arquivo> doModulo(@AuthenticationPrincipal Jwt jwt, @PathVariable Long moduloId) {
        return arquivoAlunoService.doModulo(usuario(jwt), moduloId).stream()
                .map(Arquivo::de)
                .toList();
    }

    @GetMapping("/arquivos/{id}/download")
    @Operation(summary = "Baixar arquivo", description = "Confere o acesso e devolve uma URL válida por 5 minutos.")
    public ResponseEntity<DownloadResponse> download(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(DownloadResponse.de(arquivoAlunoService.download(usuario(jwt), id)));
    }

    private static Long usuario(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
