package br.com.celke.cursos.perfil.api;

import br.com.celke.cursos.auth.api.CookieRefresh;
import br.com.celke.cursos.perfil.application.PerfilService;
import br.com.celke.cursos.perfil.application.PerfilService.EnvioFoto;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.PoliticaSenha;
import br.com.celke.cursos.usuario.domain.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Perfil do próprio usuário (aluno ou admin), sempre pelo id do token. */
@RestController
@RequestMapping("/api/perfil")
@Tag(name = "Perfil")
public class PerfilController {

    private final PerfilService perfilService;
    private final CookieRefresh cookieRefresh;

    public PerfilController(PerfilService perfilService, CookieRefresh cookieRefresh) {
        this.perfilService = perfilService;
        this.cookieRefresh = cookieRefresh;
    }

    @GetMapping
    @Operation(summary = "Meu perfil", description = "A URL da foto é temporária (vale uma hora).")
    public PerfilResponse buscar(@AuthenticationPrincipal Jwt jwt) {
        return resposta(perfilService.buscar(usuario(jwt)));
    }

    @PutMapping
    @Operation(summary = "Atualizar o nome")
    public PerfilResponse atualizar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AtualizarPerfil request) {
        return resposta(perfilService.renomear(usuario(jwt), request.nome()));
    }

    @PutMapping("/senha")
    @Operation(
            summary = "Trocar a senha",
            description = "Exige a senha atual. Encerra todas as sessões (o cookie de refresh é limpo).")
    public ResponseEntity<Void> trocarSenha(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TrocarSenha request) {
        perfilService.trocarSenha(usuario(jwt), request.senhaAtual(), request.novaSenha());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRefresh.limpar().toString())
                .build();
    }

    @PostMapping("/foto/envio")
    @Operation(
            summary = "Iniciar o envio da foto",
            description = "JPEG ou PNG até 5 MB. Devolve a URL para enviar direto ao armazenamento.")
    public EnvioFotoResponse iniciarEnvioFoto(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody IniciarEnvioFoto request) {
        return EnvioFotoResponse.de(
                perfilService.iniciarEnvioFoto(usuario(jwt), request.contentType(), request.tamanho()));
    }

    @PutMapping("/foto")
    @Operation(summary = "Confirmar a foto", description = "Reduz para até 400 × 400 e regrava em JPEG sem metadados.")
    public PerfilResponse confirmarFoto(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConfirmarFoto request) {
        return resposta(perfilService.confirmarFoto(usuario(jwt), request.chave()));
    }

    @DeleteMapping("/foto")
    @Operation(summary = "Remover a foto")
    public ResponseEntity<Void> removerFoto(@AuthenticationPrincipal Jwt jwt) {
        perfilService.removerFoto(usuario(jwt));
        return ResponseEntity.noContent().build();
    }

    private PerfilResponse resposta(Usuario usuario) {
        return PerfilResponse.de(
                usuario, perfilService.urlFoto(usuario).map(URL::toString).orElse(null));
    }

    private static Long usuario(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    public record AtualizarPerfil(@NotBlank @Size(max = 120) String nome) {}

    /** O mínimo da nova senha depende do papel (aluno 10, admin 12) e é conferido no serviço. */
    public record TrocarSenha(
            @NotBlank @Size(max = PoliticaSenha.MAXIMO) String senhaAtual,
            @NotBlank @Size(max = PoliticaSenha.MAXIMO) String novaSenha) {}

    public record IniciarEnvioFoto(
            @NotBlank String contentType, @Positive long tamanho) {}

    public record ConfirmarFoto(@NotBlank @Size(max = 200) String chave) {}

    /** Nunca inclui senha, hash ou tokens. */
    public record PerfilResponse(Long id, String nome, String email, List<Papel> papeis, String fotoUrl) {

        static PerfilResponse de(Usuario usuario, String fotoUrl) {
            return new PerfilResponse(
                    usuario.getId(),
                    usuario.getNome(),
                    usuario.getEmail(),
                    usuario.getPapeis().stream().sorted().toList(),
                    fotoUrl);
        }
    }

    public record EnvioFotoResponse(String chave, String url, String contentType, Instant expiraEm) {

        static EnvioFotoResponse de(EnvioFoto envio) {
            return new EnvioFotoResponse(envio.chave(), envio.url().toString(), envio.contentType(), envio.expiraEm());
        }
    }
}
