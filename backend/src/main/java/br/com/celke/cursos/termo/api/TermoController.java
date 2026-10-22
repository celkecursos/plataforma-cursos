package br.com.celke.cursos.termo.api;

import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.termo.application.TermoService;
import br.com.celke.cursos.termo.domain.Termo;
import br.com.celke.cursos.termo.domain.TipoTermo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Termos")
public class TermoController {

    private final TermoService termoService;

    public TermoController(TermoService termoService) {
        this.termoService = termoService;
    }

    @GetMapping("/api/termos/vigentes")
    @Operation(summary = "Termos vigentes", description = "Público: a versão atual de cada termo (páginas do site).")
    public List<TermoResponse> vigentes() {
        return termoService.vigentes().stream().map(TermoResponse::de).toList();
    }

    @GetMapping("/api/termos/pendentes")
    @Operation(
            summary = "Termos a aceitar",
            description = "Versões vigentes que o usuário da sessão ainda não aceitou.")
    public List<TermoResponse> pendentes(@AuthenticationPrincipal Jwt jwt) {
        return termoService.pendentes(Long.valueOf(jwt.getSubject())).stream()
                .map(TermoResponse::de)
                .toList();
    }

    @PostMapping("/api/termos/{id}/aceite")
    @Operation(summary = "Aceitar termo", description = "Só a versão vigente; registra IP e navegador.")
    public ResponseEntity<Void> aceitar(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long id, HttpServletRequest request) {
        termoService.aceitar(Long.valueOf(jwt.getSubject()), id, OrigemRequisicao.de(request));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/adm/termos")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Publicar nova versão",
            description =
                    "Texto em Markdown. Todos os usuários precisam aceitar a versão nova para usar a área do aluno.")
    public ResponseEntity<TermoResponse> publicar(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Publicar request) {
        TermoResponse termo = TermoResponse.de(
                termoService.publicar(Long.valueOf(jwt.getSubject()), request.tipo(), request.conteudo()));
        return ResponseEntity.created(URI.create("/api/adm/termos?tipo=" + termo.tipo()))
                .body(termo);
    }

    @GetMapping("/api/adm/termos")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Versões publicadas", description = "Da mais nova para a mais antiga.")
    public List<TermoResponse> historico(@RequestParam TipoTermo tipo) {
        return termoService.historico(tipo).stream().map(TermoResponse::de).toList();
    }

    public record Publicar(
            @NotNull TipoTermo tipo,
            @NotBlank @Size(max = 100_000) String conteudo) {}

    /** O texto sai como HTML filtrado. */
    public record TermoResponse(Long id, TipoTermo tipo, int versao, String conteudoHtml, Instant publicadoEm) {

        static TermoResponse de(Termo t) {
            return new TermoResponse(t.getId(), t.getTipo(), t.getVersao(), t.getConteudoHtml(), t.getPublicadoEm());
        }
    }
}
