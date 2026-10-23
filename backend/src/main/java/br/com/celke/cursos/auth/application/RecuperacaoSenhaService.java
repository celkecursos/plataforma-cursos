package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.domain.TipoToken;
import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.shared.erro.CampoInvalidoException;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.PoliticaSenha;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.util.Map;
import java.util.Optional;
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
     * Troca a senha pelo link. A política (com o mínimo do papel: admin exige mais) é conferida antes de
     * usar o link, para a pessoa poder tentar outra senha. Também confirma o e-mail (o link chegou à
     * caixa do dono) e encerra todas as sessões, inclusive a de quem tiver descoberto a senha antiga.
     */
    @Transactional
    public void redefinir(String token, String novaSenha) {
        Usuario usuario = tokens.dono(token, TipoToken.RECUPERACAO_SENHA)
                .flatMap(usuarioService::buscarPorId)
                .filter(Usuario::isAtivo)
                .orElseThrow(LinkInvalidoException::new);
        int minimo =
                usuario.getPapeis().contains(Papel.ADMIN) ? PoliticaSenha.MINIMO_ADMIN : PoliticaSenha.MINIMO_ALUNO;
        String problema = PoliticaSenha.validar(novaSenha, minimo, usuario.getNome(), usuario.getEmail());
        if (problema != null) {
            throw new CampoInvalidoException("senha", problema);
        }
        tokens.consumir(token, TipoToken.RECUPERACAO_SENHA).orElseThrow(LinkInvalidoException::new);
        usuarioService.trocarSenha(usuario, novaSenha);
        usuarioService.confirmarEmail(usuario);
        refreshTokenService.revogarTodas(usuario.getId());
    }

    /**
     * Link de "criar senha" para a conta criada por uma compra (o mesmo token da recuperação, com a
     * mesma validade). Vazio se a conta já recebeu o máximo de links na última hora.
     */
    @Transactional
    public Optional<LinkSenha> linkCriarSenha(Long usuarioId) {
        return tokens.emitir(usuarioId, TipoToken.RECUPERACAO_SENHA)
                .map(token -> new LinkSenha(
                        props.urlAluno() + "/redefinir-senha?token=" + token + "&origem=pagamento",
                        Validade.texto(tokens.validade(TipoToken.RECUPERACAO_SENHA))));
    }

    /** Link de criar senha e a validade escrita para o e-mail ("1 hora"). */
    public record LinkSenha(String url, String validade) {}

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
