package br.com.celke.cursos.certificado.api;

import br.com.celke.cursos.certificado.api.CertificadoResponses.CertificadoAluno;
import br.com.celke.cursos.certificado.api.CertificadoResponses.DownloadResponse;
import br.com.celke.cursos.certificado.api.CertificadoResponses.SituacaoResponse;
import br.com.celke.cursos.certificado.api.CertificadoResponses.Validacao;
import br.com.celke.cursos.certificado.application.CertificadoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Certificados")
public class CertificadoController {

    private final CertificadoService certificadoService;

    public CertificadoController(CertificadoService certificadoService) {
        this.certificadoService = certificadoService;
    }

    @GetMapping("/api/aluno/turmas/{turmaId}/certificado")
    @Operation(
            summary = "Situação do certificado na turma",
            description = "Percentual visto e exigido; traz o certificado se já emitido. Exige matrícula vigente.")
    public SituacaoResponse situacao(@AuthenticationPrincipal Jwt jwt, @PathVariable Long turmaId) {
        return SituacaoResponse.de(certificadoService.situacao(usuario(jwt), turmaId));
    }

    @PostMapping("/api/aluno/turmas/{turmaId}/certificado")
    @Operation(
            summary = "Emitir o certificado",
            description = "Com o nome completo confirmado pelo aluno. 201 na emissão; 200 se já existia.")
    public ResponseEntity<CertificadoAluno> emitir(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long turmaId, @Valid @RequestBody Emitir request) {
        var emissao = certificadoService.emitir(usuario(jwt), turmaId, request.nomeCompleto());
        CertificadoAluno certificado = CertificadoAluno.de(emissao.certificado());
        return emissao.novo()
                ? ResponseEntity.created(URI.create("/api/certificados/" + certificado.codigo()))
                        .body(certificado)
                : ResponseEntity.ok(certificado);
    }

    @GetMapping("/api/aluno/certificados")
    @Operation(summary = "Meus certificados", description = "Também os de matrículas já encerradas.")
    public List<CertificadoAluno> meus(@AuthenticationPrincipal Jwt jwt) {
        return certificadoService.meus(usuario(jwt)).stream()
                .map(CertificadoAluno::de)
                .toList();
    }

    @GetMapping("/api/aluno/certificados/{id}/download")
    @Operation(summary = "Baixar o PDF", description = "URL válida por 5 minutos; certificado de outro aluno, 404.")
    public ResponseEntity<DownloadResponse> download(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        var download = certificadoService.download(usuario(jwt), id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new DownloadResponse(download.url().toString(), download.expiraEm()));
    }

    @GetMapping("/api/certificados/{codigo}")
    @Operation(
            summary = "Validar certificado",
            description = "Público: confere o código impresso no certificado e devolve os dados dele.")
    public Validacao validar(@PathVariable String codigo) {
        return Validacao.de(certificadoService.validar(codigo));
    }

    private static Long usuario(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    public record Emitir(@NotBlank @Size(max = 150) String nomeCompleto) {}
}
