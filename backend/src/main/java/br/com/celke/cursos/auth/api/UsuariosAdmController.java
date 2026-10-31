package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.auth.application.UsuariosAdmService;
import br.com.celke.cursos.shared.web.Pagina;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/adm/usuarios")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ADM — usuários")
public class UsuariosAdmController {

    private final UsuariosAdmService service;

    public UsuariosAdmController(UsuariosAdmService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Usuários", description = "Busca por nome ou e-mail, papel e situação; ordem por nome.")
    public Pagina<UsuarioAdm> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Papel papel,
            @RequestParam(required = false) Boolean ativo,
            @ParameterObject Pageable pagina) {
        return Pagina.de(service.listar(busca, papel, ativo, pagina).map(this::resposta));
    }

    @PatchMapping("/{id}/situacao")
    @Operation(
            summary = "Bloquear ou desbloquear",
            description = "Bloquear encerra as sessões na hora e impede o login. O admin não bloqueia a si mesmo.")
    public UsuarioAdm alterarSituacao(
            @PathVariable Long id, @Valid @RequestBody Situacao request, @AuthenticationPrincipal Jwt jwt) {
        return resposta(service.alterarSituacao(id, request.ativo(), Long.valueOf(jwt.getSubject())));
    }

    @PostMapping("/{id}/encerrar-sessoes")
    @Operation(
            summary = "Encerrar as sessões",
            description = "Sai de todos os dispositivos: o usuário precisa entrar de novo.")
    public ResponseEntity<Void> encerrarSessoes(@PathVariable Long id) {
        service.encerrarSessoes(id);
        return ResponseEntity.noContent().build();
    }

    private UsuarioAdm resposta(Usuario u) {
        return new UsuarioAdm(
                u.getId(),
                u.getNome(),
                u.getEmail(),
                u.getPapeis().stream().sorted().toList(),
                u.isAtivo(),
                u.isEmailConfirmado(),
                service.segundoFatorAtivo(u.getId()),
                u.getSessoesEncerradasEm(),
                u.getCriadoEm());
    }

    public record Situacao(@NotNull Boolean ativo) {}

    public record UsuarioAdm(
            Long id,
            String nome,
            String email,
            List<Papel> papeis,
            boolean ativo,
            boolean emailConfirmado,
            boolean segundoFatorAtivo,
            Instant sessoesEncerradasEm,
            Instant criadoEm) {}
}
