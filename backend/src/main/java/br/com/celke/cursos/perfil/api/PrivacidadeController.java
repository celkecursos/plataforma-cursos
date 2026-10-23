package br.com.celke.cursos.perfil.api;

import br.com.celke.cursos.auth.api.CookieRefresh;
import br.com.celke.cursos.perfil.application.PrivacidadeService;
import br.com.celke.cursos.usuario.domain.PoliticaSenha;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/perfil")
@Tag(name = "Perfil — privacidade")
public class PrivacidadeController {

    private final PrivacidadeService privacidadeService;
    private final CookieRefresh cookieRefresh;

    public PrivacidadeController(PrivacidadeService privacidadeService, CookieRefresh cookieRefresh) {
        this.privacidadeService = privacidadeService;
        this.cookieRefresh = cookieRefresh;
    }

    @GetMapping("/dados")
    @Operation(
            summary = "Baixar meus dados",
            description = "Todos os dados da conta em JSON (conta, matrículas, progresso, fórum, certificados,"
                    + " pedidos, aceites dos termos, entradas e acessos aos vídeos).")
    public ResponseEntity<Map<String, Object>> dados(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("meus-dados.json")
                                .build()
                                .toString())
                .body(privacidadeService.exportar(Long.valueOf(jwt.getSubject())));
    }

    @PostMapping("/exclusao")
    @Operation(
            summary = "Excluir minha conta",
            description = "Exige a senha e a palavra EXCLUIR. Apaga ou anonimiza os dados, encerra as sessões"
                    + " e avisa por e-mail. Não vale para administradores.")
    public ResponseEntity<Void> excluir(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Exclusao request) {
        privacidadeService.excluirConta(Long.valueOf(jwt.getSubject()), request.senha());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRefresh.limpar().toString())
                .build();
    }

    public record Exclusao(
            @NotBlank @Size(max = PoliticaSenha.MAXIMO) String senha,

            @NotBlank @Pattern(regexp = "EXCLUIR", message = "Digite EXCLUIR para confirmar.")
            String confirmacao) {}
}
