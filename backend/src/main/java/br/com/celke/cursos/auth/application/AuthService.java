package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.application.RefreshTokenService.RefreshEmitido;
import br.com.celke.cursos.auth.application.TokenAcessoService.TokenAcesso;
import br.com.celke.cursos.auth.domain.ResultadoLogin;
import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Login (com bloqueio por falhas seguidas e auditoria), renovação e saída. */
@Service
public class AuthService {

    private final UsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;
    private final TokenAcessoService tokenAcessoService;
    private final RefreshTokenService refreshTokenService;
    private final BloqueioLoginService bloqueioLoginService;
    private final AuditoriaLoginService auditoriaLoginService;

    /**
     * Hash de uma senha aleatória: conferido quando o e-mail não existe, para o login levar o mesmo
     * tempo nos dois casos e não revelar quais e-mails estão cadastrados.
     */
    private final String hashFicticio;

    public AuthService(
            UsuarioService usuarioService,
            PasswordEncoder passwordEncoder,
            TokenAcessoService tokenAcessoService,
            RefreshTokenService refreshTokenService,
            BloqueioLoginService bloqueioLoginService,
            AuditoriaLoginService auditoriaLoginService) {
        this.usuarioService = usuarioService;
        this.passwordEncoder = passwordEncoder;
        this.tokenAcessoService = tokenAcessoService;
        this.refreshTokenService = refreshTokenService;
        this.bloqueioLoginService = bloqueioLoginService;
        this.auditoriaLoginService = auditoriaLoginService;
        this.hashFicticio = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Confere o bloqueio antes de tudo (sem calcular hash), depois a senha. Falha e sucesso
     * atualizam os contadores de bloqueio e ficam na auditoria. Toda falha recebe a mesma mensagem;
     * só quem acertou a senha fica sabendo que falta confirmar o e-mail.
     */
    @Transactional
    public Sessao entrar(String email, String senha, OrigemRequisicao origem) {
        try {
            bloqueioLoginService.verificar(email, origem.ip());
        } catch (MuitasTentativasException e) {
            auditoriaLoginService.registrar(email, null, ResultadoLogin.BLOQUEADO, origem);
            throw e;
        }
        Optional<Usuario> encontrado = usuarioService.buscarPorEmail(email);
        // Conta sem senha (criada por uma compra) confere o hash fictício e falha como senha errada
        boolean possuiSenha = encontrado.filter(Usuario::possuiSenha).isPresent();
        String hash = possuiSenha ? encontrado.get().getSenhaHash() : hashFicticio;
        boolean senhaConfere = passwordEncoder.matches(senha, hash) && possuiSenha;
        ResultadoLogin resultado = resultado(encontrado, senhaConfere);
        auditoriaLoginService.registrar(email, encontrado.orElse(null), resultado, origem);
        if (resultado == ResultadoLogin.EMAIL_NAO_CONFIRMADO) {
            // Quem chegou aqui sabe a senha: pode saber que falta confirmar o e-mail
            bloqueioLoginService.registrarSucesso(email, origem.ip());
            throw new EmailNaoConfirmadoException();
        }
        if (resultado != ResultadoLogin.SUCESSO) {
            bloqueioLoginService.registrarFalha(email, origem.ip());
            throw new CredenciaisInvalidasException();
        }
        bloqueioLoginService.registrarSucesso(email, origem.ip());
        Usuario usuario = encontrado.get();
        usuarioService.atualizarHashSeNecessario(usuario, senha);
        return abrirSessao(usuario, refreshTokenService.emitir(usuario.getId()));
    }

    private static ResultadoLogin resultado(Optional<Usuario> encontrado, boolean senhaConfere) {
        if (encontrado.isEmpty()) {
            return ResultadoLogin.EMAIL_DESCONHECIDO;
        }
        if (!senhaConfere) {
            return ResultadoLogin.SENHA_INVALIDA;
        }
        if (!encontrado.get().isAtivo()) {
            return ResultadoLogin.USUARIO_INATIVO;
        }
        return encontrado.get().isEmailConfirmado() ? ResultadoLogin.SUCESSO : ResultadoLogin.EMAIL_NAO_CONFIRMADO;
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
