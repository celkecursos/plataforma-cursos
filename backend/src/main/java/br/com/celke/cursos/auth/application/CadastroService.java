package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.domain.TipoToken;
import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro do aluno e confirmação do e-mail. A resposta ao cliente é a mesma para e-mail novo e já
 * cadastrado; quem descobre o que aconteceu é o dono da caixa, pelo e-mail que recebe.
 */
@Service
public class CadastroService {

    private final UsuarioService usuarioService;
    private final TokenUsoUnicoService tokens;
    private final EmailService emailService;
    private final ContaProperties props;

    /** Contas que já receberam o aviso de "cadastro com e-mail existente" na última hora. */
    private final Cache<Long, Boolean> avisosEnviados = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    public CadastroService(
            UsuarioService usuarioService,
            TokenUsoUnicoService tokens,
            EmailService emailService,
            ContaProperties props) {
        this.usuarioService = usuarioService;
        this.tokens = tokens;
        this.emailService = emailService;
        this.props = props;
    }

    /**
     * E-mail novo: cria a conta pendente e envia o link de confirmação. E-mail com cadastro pendente:
     * reenvia o link (a senha informada é ignorada). E-mail de conta ativa: avisa o dono.
     */
    @Transactional
    public void cadastrar(String nome, String email, String senha) {
        Optional<Usuario> existente = usuarioService.buscarPorEmail(email);
        if (existente.isPresent()) {
            tratarEmailExistente(existente.get());
            return;
        }
        try {
            enviarConfirmacao(usuarioService.cadastrarAluno(nome, email, senha));
        } catch (ConflitoException e) {
            // Outro cadastro com o mesmo e-mail chegou junto: a resposta continua a mesma
        }
    }

    @Transactional
    public void reenviarConfirmacao(String email) {
        usuarioService
                .buscarPorEmail(email)
                .filter(u -> u.isAtivo() && !u.isEmailConfirmado())
                .ifPresent(this::enviarConfirmacao);
    }

    @Transactional
    public void confirmarEmail(String token) {
        Long usuarioId = tokens.consumir(token, TipoToken.CONFIRMACAO_EMAIL).orElseThrow(LinkInvalidoException::new);
        Usuario usuario = usuarioService.buscarPorId(usuarioId).orElseThrow(LinkInvalidoException::new);
        usuarioService.confirmarEmail(usuario);
    }

    private void tratarEmailExistente(Usuario usuario) {
        if (!usuario.isAtivo()) {
            return;
        }
        if (!usuario.isEmailConfirmado()) {
            enviarConfirmacao(usuario);
            return;
        }
        if (avisosEnviados.asMap().putIfAbsent(usuario.getId(), Boolean.TRUE) == null) {
            emailService.enviar(
                    usuario.getEmail(),
                    "Tentativa de cadastro com o seu e-mail",
                    "cadastro-existente",
                    Map.of(
                            "nome", usuario.getNome(),
                            "linkEntrar", props.urlAluno() + "/entrar",
                            "linkRecuperar", props.urlAluno() + "/recuperar-senha"));
        }
    }

    private void enviarConfirmacao(Usuario usuario) {
        tokens.emitir(usuario.getId(), TipoToken.CONFIRMACAO_EMAIL)
                .ifPresent(token -> emailService.enviar(
                        usuario.getEmail(),
                        "Confirme o seu e-mail",
                        "confirmacao-email",
                        Map.of(
                                "nome", usuario.getNome(),
                                "link", props.urlAluno() + "/confirmar-email?token=" + token,
                                "validade", Validade.texto(tokens.validade(TipoToken.CONFIRMACAO_EMAIL)))));
    }
}
