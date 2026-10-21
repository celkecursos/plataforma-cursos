package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.auth.application.AuthService;
import br.com.celke.cursos.auth.application.AuthService.Sessao;
import br.com.celke.cursos.auth.application.SessaoInvalidaException;
import br.com.celke.cursos.usuario.application.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação")
public class AuthController {

    private final AuthService authService;
    private final UsuarioService usuarioService;
    private final CookieRefresh cookieRefresh;

    public AuthController(AuthService authService, UsuarioService usuarioService, CookieRefresh cookieRefresh) {
        this.authService = authService;
        this.usuarioService = usuarioService;
        this.cookieRefresh = cookieRefresh;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Entrar com e-mail e senha",
            description = "Devolve o access token e grava o refresh token em cookie.")
    public ResponseEntity<SessaoResponse> login(@Valid @RequestBody LoginRequest request) {
        return comCookie(authService.entrar(request.email(), request.senha()));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Renovar a sessão",
            description = "Troca o refresh token do cookie por um novo e emite outro access token.")
    public ResponseEntity<SessaoResponse> refresh(
            @CookieValue(name = CookieRefresh.NOME, required = false) String refreshToken) {
        return comCookie(authService.renovar(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "Sair", description = "Revoga a sessão do cookie e apaga o cookie.")
    public ResponseEntity<Void> logout(@CookieValue(name = CookieRefresh.NOME, required = false) String refreshToken) {
        authService.sair(refreshToken);
        return semCookie();
    }

    @PostMapping("/sair-de-todos")
    @Operation(summary = "Sair de todos os dispositivos", description = "Revoga todas as sessões do usuário.")
    public ResponseEntity<Void> sairDeTodos(@AuthenticationPrincipal Jwt jwt) {
        authService.sairDeTodos(Long.valueOf(jwt.getSubject()));
        return semCookie();
    }

    @GetMapping("/me")
    @Operation(summary = "Usuário da sessão")
    public UsuarioResponse me(@AuthenticationPrincipal Jwt jwt) {
        return usuarioService
                .buscarPorId(Long.valueOf(jwt.getSubject()))
                .map(UsuarioResponse::de)
                .orElseThrow(SessaoInvalidaException::new);
    }

    private ResponseEntity<SessaoResponse> comCookie(Sessao sessao) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookieRefresh.criar(sessao.refresh().token()).toString())
                .body(SessaoResponse.de(sessao));
    }

    private ResponseEntity<Void> semCookie() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRefresh.limpar().toString())
                .build();
    }
}
