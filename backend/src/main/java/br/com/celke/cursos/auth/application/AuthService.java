package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.application.RefreshTokenService.RefreshEmitido;
import br.com.celke.cursos.auth.application.TokenAcessoService.TokenAcesso;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Login, renovação e saída. */
@Service
public class AuthService {

    private final UsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;
    private final TokenAcessoService tokenAcessoService;
    private final RefreshTokenService refreshTokenService;

    /**
     * Hash de uma senha aleatória: conferido quando o e-mail não existe, para o login levar o mesmo
     * tempo nos dois casos e não revelar quais e-mails estão cadastrados.
     */
    private final String hashFicticio;

    public AuthService(
            UsuarioService usuarioService,
            PasswordEncoder passwordEncoder,
            TokenAcessoService tokenAcessoService,
            RefreshTokenService refreshTokenService) {
        this.usuarioService = usuarioService;
        this.passwordEncoder = passwordEncoder;
        this.tokenAcessoService = tokenAcessoService;
        this.refreshTokenService = refreshTokenService;
        this.hashFicticio = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public Sessao entrar(String email, String senha) {
        Optional<Usuario> encontrado = usuarioService.buscarPorEmail(email);
        String hash = encontrado.map(Usuario::getSenhaHash).orElse(hashFicticio);
        boolean senhaConfere = passwordEncoder.matches(senha, hash);
        if (encontrado.isEmpty() || !senhaConfere || !encontrado.get().isAtivo()) {
            throw new CredenciaisInvalidasException();
        }
        Usuario usuario = encontrado.get();
        usuarioService.atualizarHashSeNecessario(usuario, senha);
        return abrirSessao(usuario, refreshTokenService.emitir(usuario.getId()));
    }

    /**
     * Troca o refresh token por um novo e emite outro access token. Sem transação própria: a
     * revogação de uma família por reutilização precisa ficar gravada mesmo com a sessão recusada.
     */
    public Sessao renovar(String refreshToken) {
        RefreshEmitido novo = refreshTokenService.rotacionar(refreshToken).orElseThrow(SessaoInvalidaException::new);
        Usuario usuario = usuarioService
                .buscarPorId(novo.usuarioId())
                .filter(Usuario::isAtivo)
                .orElseThrow(SessaoInvalidaException::new);
        return abrirSessao(usuario, novo);
    }

    public void sair(String refreshToken) {
        refreshTokenService.revogarFamiliaDoToken(refreshToken);
    }

    public void sairDeTodos(Long usuarioId) {
        refreshTokenService.revogarTodas(usuarioId);
    }

    private Sessao abrirSessao(Usuario usuario, RefreshEmitido refresh) {
        return new Sessao(usuario, tokenAcessoService.emitir(usuario), refresh);
    }

    public record Sessao(Usuario usuario, TokenAcesso acesso, RefreshEmitido refresh) {}
}
