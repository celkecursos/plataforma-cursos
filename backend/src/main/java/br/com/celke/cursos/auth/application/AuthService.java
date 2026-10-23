package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.application.RefreshTokenService.RefreshEmitido;
import br.com.celke.cursos.auth.application.SegundoFatorService.Cadastro;
import br.com.celke.cursos.auth.application.SegundoFatorService.Verificacao;
import br.com.celke.cursos.auth.application.TokenAcessoService.TokenAcesso;
import br.com.celke.cursos.auth.domain.ResultadoLogin;
import br.com.celke.cursos.auth.domain.TipoToken;
import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login (com bloqueio por falhas seguidas e auditoria), segundo fator dos administradores, renovação e
 * saída.
 */
@Service
public class AuthService {

    private final UsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;
    private final TokenAcessoService tokenAcessoService;
    private final RefreshTokenService refreshTokenService;
    private final BloqueioLoginService bloqueioLoginService;
    private final AuditoriaLoginService auditoriaLoginService;
    private final SegundoFatorService segundoFatorService;
    private final TokenUsoUnicoService tokens;
    private final Clock clock;

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
            AuditoriaLoginService auditoriaLoginService,
            SegundoFatorService segundoFatorService,
            TokenUsoUnicoService tokens,
            Clock clock) {
        this.usuarioService = usuarioService;
        this.passwordEncoder = passwordEncoder;
        this.tokenAcessoService = tokenAcessoService;
        this.refreshTokenService = refreshTokenService;
        this.bloqueioLoginService = bloqueioLoginService;
        this.auditoriaLoginService = auditoriaLoginService;
        this.segundoFatorService = segundoFatorService;
        this.tokens = tokens;
        this.clock = clock;
        this.hashFicticio = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Confere o bloqueio antes de tudo (sem calcular hash), depois a senha. Falha e sucesso
     * atualizam os contadores de bloqueio e ficam na auditoria. Toda falha recebe a mesma mensagem;
     * só quem acertou a senha fica sabendo que falta confirmar o e-mail. O administrador que acerta a
     * senha recebe um desafio (sem sessão) para a segunda etapa.
     */
    @Transactional
    public Entrada entrar(String email, String senha, OrigemRequisicao origem) {
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
        if (resultado == ResultadoLogin.SUCESSO && SegundoFatorService.exigido(encontrado.get())) {
            resultado = ResultadoLogin.SEGUNDO_FATOR_PENDENTE;
        }
        auditoriaLoginService.registrar(email, encontrado.orElse(null), resultado, origem);
        if (resultado == ResultadoLogin.EMAIL_NAO_CONFIRMADO) {
            // Quem chegou aqui sabe a senha: pode saber que falta confirmar o e-mail
            bloqueioLoginService.registrarSucesso(email, origem.ip());
            throw new EmailNaoConfirmadoException();
        }
        if (resultado != ResultadoLogin.SUCESSO && resultado != ResultadoLogin.SEGUNDO_FATOR_PENDENTE) {
            bloqueioLoginService.registrarFalha(email, origem.ip());
            throw new CredenciaisInvalidasException();
        }
        Usuario usuario = encontrado.get();
        usuarioService.atualizarHashSeNecessario(usuario, senha);
        if (resultado == ResultadoLogin.SEGUNDO_FATOR_PENDENTE) {
            // Os contadores de falha só zeram quando a segunda etapa passar
            String desafio = tokens.emitir(usuario.getId(), TipoToken.DESAFIO_SEGUNDO_FATOR)
                    .orElseThrow();
            Instant expiraEm = clock.instant().plus(tokens.validade(TipoToken.DESAFIO_SEGUNDO_FATOR));
            return new Desafio(desafio, !segundoFatorService.ativo(usuario.getId()), expiraEm);
        }
        bloqueioLoginService.registrarSucesso(email, origem.ip());
        return abrirSessao(usuario, refreshTokenService.emitir(usuario.getId()));
    }

    /** Primeiro acesso do administrador: gera o segredo do aplicativo autenticador. */
    @Transactional
    public Cadastro cadastrarSegundoFator(String desafio) {
        return segundoFatorService.iniciarCadastro(donoDoDesafio(desafio));
    }

    /**
     * Segunda etapa: confere o código do aplicativo (ou de recuperação) e abre a sessão. Código errado
     * conta no bloqueio como uma senha errada; o desafio vale até vencer ou ser usado.
     */
    @Transactional(noRollbackFor = CodigoInvalidoException.class)
    public SessaoSegundoFator concluirSegundoFator(
            String desafio, String codigo, String codigoRecuperacao, OrigemRequisicao origem) {
        Usuario usuario = donoDoDesafio(desafio);
        try {
            bloqueioLoginService.verificar(usuario.getEmail(), origem.ip());
        } catch (MuitasTentativasException e) {
            auditoriaLoginService.registrar(usuario.getEmail(), usuario, ResultadoLogin.BLOQUEADO, origem);
            throw e;
        }
        Verificacao verificacao = segundoFatorService.verificar(usuario, codigo, codigoRecuperacao);
        if (!verificacao.valida()) {
            auditoriaLoginService.registrar(usuario.getEmail(), usuario, ResultadoLogin.SEGUNDO_FATOR_INVALIDO, origem);
            bloqueioLoginService.registrarFalha(usuario.getEmail(), origem.ip());
            throw new CodigoInvalidoException();
        }
        tokens.consumir(desafio, TipoToken.DESAFIO_SEGUNDO_FATOR).orElseThrow(DesafioInvalidoException::new);
        auditoriaLoginService.registrar(usuario.getEmail(), usuario, verificacao.resultado(), origem);
        bloqueioLoginService.registrarSucesso(usuario.getEmail(), origem.ip());
        Sessao sessao = abrirSessao(usuario, refreshTokenService.emitir(usuario.getId()));
        return new SessaoSegundoFator(sessao, verificacao.codigosRecuperacao());
    }

    private Usuario donoDoDesafio(String desafio) {
        return tokens.dono(desafio, TipoToken.DESAFIO_SEGUNDO_FATOR)
                .flatMap(usuarioService::buscarPorId)
                .filter(Usuario::isAtivo)
                .orElseThrow(DesafioInvalidoException::new);
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

    /** Resultado da primeira etapa do login: a sessão ou o desafio do segundo fator. */
    public sealed interface Entrada permits Sessao, Desafio {}

    public record Sessao(Usuario usuario, TokenAcesso acesso, RefreshEmitido refresh) implements Entrada {}

    /** {@code cadastro}: o administrador ainda não cadastrou o aplicativo autenticador. */
    public record Desafio(String token, boolean cadastro, Instant expiraEm) implements Entrada {}

    /** Os códigos de recuperação só vêm quando o segundo fator acabou de ser ativado. */
    public record SessaoSegundoFator(Sessao sessao, List<String> codigosRecuperacao) {}
}
