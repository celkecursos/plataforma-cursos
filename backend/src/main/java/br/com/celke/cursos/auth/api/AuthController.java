package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.auth.api.SegundoFatorDtos.CadastroRequest;
import br.com.celke.cursos.auth.api.SegundoFatorDtos.CadastroResponse;
import br.com.celke.cursos.auth.api.SegundoFatorDtos.ConfirmacaoRequest;
import br.com.celke.cursos.auth.api.SegundoFatorDtos.DesafioResponse;
import br.com.celke.cursos.auth.application.AuthService;
import br.com.celke.cursos.auth.application.AuthService.Desafio;
import br.com.celke.cursos.auth.application.AuthService.Sessao;
import br.com.celke.cursos.auth.application.SessaoInvalidaException;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.usuario.application.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
    private final VerificadorOrigem verificadorOrigem;

    public AuthController(
            AuthService authService,
            UsuarioService usuarioService,
            CookieRefresh cookieRefresh,
            VerificadorOrigem verificadorOrigem) {
        this.authService = authService;
        this.usuarioService = usuarioService;
        this.cookieRefresh = cookieRefresh;
        this.verificadorOrigem = verificadorOrigem;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Entrar com e-mail e senha",
            description = "Devolve o access token e grava o refresh token em cookie. Para administradores, "
                    + "devolve o desafio do segundo fator (sem sessão): a entrada termina em "
                    + "POST /api/auth/segundo-fator.")
    @ApiResponse(
            responseCode = "200",
            description = "Sessão aberta ou desafio do segundo fator",
            content = @Content(schema = @Schema(oneOf = {SessaoResponse.class, DesafioResponse.class})))
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return switch (authService.entrar(request.email(), request.senha(), OrigemRequisicao.de(http))) {
            case Sessao sessao -> comCookie(sessao);
            case Desafio desafio -> ResponseEntity.ok(DesafioResponse.de(desafio));
        };
    }

    @PostMapping("/segundo-fator/cadastro")
    @Operation(
            summary = "Cadastrar o aplicativo autenticador",
            description = "Primeiro acesso do administrador: devolve o segredo e o endereço do QR code. "
                    + "O cadastro só vale depois do primeiro código certo.")
    public CadastroResponse cadastrarSegundoFator(@Valid @RequestBody CadastroRequest request) {
        return CadastroResponse.de(authService.cadastrarSegundoFator(request.desafio()));
    }

    @PostMapping("/segundo-fator")
    @Operation(
            summary = "Concluir a entrada com o segundo fator",
            description = "Confere o código do aplicativo (ou um código de recuperação), devolve o access "
                    + "token e grava o refresh token em cookie. Na ativação, devolve os códigos de recuperação.")
    public ResponseEntity<SessaoResponse> concluirSegundoFator(
            @Valid @RequestBody ConfirmacaoRequest request, HttpServletRequest http) {
        var sessao = authService.concluirSegundoFator(
                request.desafio(), request.codigo(), request.codigoRecuperacao(), OrigemRequisicao.de(http));
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookieRefresh.criar(sessao.sessao().refresh().token()).toString())
                .body(SessaoResponse.de(sessao));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Renovar a sessão",
            description = "Troca o refresh token do cookie por um novo e emite outro access token.")
    public ResponseEntity<SessaoResponse> refresh(
            @CookieValue(name = CookieRefresh.NOME, required = false) String refreshToken, HttpServletRequest http) {
        verificadorOrigem.verificar(http);
        return comCookie(authService.renovar(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "Sair", description = "Revoga a sessão do cookie e apaga o cookie.")
    public ResponseEntity<Void> logout(
            @CookieValue(name = CookieRefresh.NOME, required = false) String refreshToken, HttpServletRequest http) {
        verificadorOrigem.verificar(http);
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
