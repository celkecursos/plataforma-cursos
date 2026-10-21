package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.domain.TipoToken;
import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recuperação de senha por link enviado ao e-mail. A resposta ao cliente é a mesma exista ou não a
 * conta. Ao redefinir, todas as sessões abertas são encerradas.
 */
@Service
public class RecuperacaoSenhaService {

    private final UsuarioService usuarioService;
    private final TokenUsoUnicoService tokens;
    private final EmailService emailService;
    private final RefreshTokenService refreshTokenService;
    private final ContaProperties props;

    public RecuperacaoSenhaService(
            UsuarioService usuarioService,
            TokenUsoUnicoService tokens,
            EmailService emailService,
            RefreshTokenService refreshTokenService,
            ContaProperties props) {
        this.usuarioService = usuarioService;
        this.tokens = tokens;
        this.emailService = emailService;
        this.refreshTokenService = refreshTokenService;
        this.props = props;
    }

    @Transactional
    public void solicitar(String email) {
        usuarioService.buscarPorEmail(email).filter(Usuario::isAtivo).ifPresent(this::enviarLink);
    }

    /**
     * Troca a senha pelo link. Também confirma o e-mail (o link chegou à caixa do dono) e encerra
     * todas as sessões, inclusive a de quem tiver descoberto a senha antiga.
     */
    @Transactional
    public void redefinir(String token, String novaSenha) {
        Long usuarioId = tokens.consumir(token, TipoToken.RECUPERACAO_SENHA).orElseThrow(LinkInvalidoException::new);
        Usuario usuario =
                usuarioService.buscarPorId(usuarioId).filter(Usuario::isAtivo).orElseThrow(LinkInvalidoException::new);
        usuarioService.trocarSenha(usuario, novaSenha);
        usuarioService.confirmarEmail(usuario);
        refreshTokenService.revogarTodas(usuario.getId());
    }

    private void enviarLink(Usuario usuario) {
        tokens.emitir(usuario.getId(), TipoToken.RECUPERACAO_SENHA)
                .ifPresent(token -> emailService.enviar(
                        usuario.getEmail(),
                        "Crie uma nova senha",
                        "recuperacao-senha",
                        Map.of(
                                "nome", usuario.getNome(),
                                "link", props.urlAluno() + "/redefinir-senha?token=" + token,
                                "validade", Validade.texto(tokens.validade(TipoToken.RECUPERACAO_SENHA)))));
    }
}
