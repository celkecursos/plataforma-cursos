package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.domain.CodigoRecuperacao;
import br.com.celke.cursos.auth.domain.ResultadoLogin;
import br.com.celke.cursos.auth.domain.SegundoFator;
import br.com.celke.cursos.auth.domain.Totp;
import br.com.celke.cursos.auth.infra.CodigoRecuperacaoRepository;
import br.com.celke.cursos.auth.infra.SegundoFatorRepository;
import br.com.celke.cursos.shared.cripto.CifraSegredos;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Segundo fator dos administradores: cadastro do aplicativo autenticador (QR code), conferência do
 * código de 6 dígitos e os códigos de recuperação de uso único. O segredo fica cifrado no banco.
 */
@Service
@EnableConfigurationProperties(SegundoFatorProperties.class)
public class SegundoFatorService {

    static final int CODIGOS_RECUPERACAO = 10;
    private static final String ALFABETO_RECUPERACAO = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final SegundoFatorRepository repository;
    private final CodigoRecuperacaoRepository codigos;
    private final SegundoFatorProperties props;
    private final CifraSegredos cifra;
    private final Clock clock;

    public SegundoFatorService(
            SegundoFatorRepository repository,
            CodigoRecuperacaoRepository codigos,
            SegundoFatorProperties props,
            Clock clock) {
        this.repository = repository;
        this.codigos = codigos;
        this.props = props;
        this.cifra = new CifraSegredos(props.chave(), "TOTP_CHAVE");
        this.clock = clock;
    }

    /** Todo administrador entra com o segundo fator. */
    public static boolean exigido(Usuario usuario) {
        return usuario.getPapeis().contains(Papel.ADMIN);
    }

    @Transactional(readOnly = true)
    public boolean ativo(Long usuarioId) {
        return repository.findById(usuarioId).filter(SegundoFator::isAtivo).isPresent();
    }

    /**
     * Gera (ou troca) o segredo de um cadastro ainda não confirmado. Quem já tem o segundo fator ativo
     * não pode cadastrar outro por aqui: perdeu o celular, usa um código de recuperação.
     */
    @Transactional
    public Cadastro iniciarCadastro(Usuario usuario) {
        Optional<SegundoFator> atual = repository.travar(usuario.getId());
        if (atual.filter(SegundoFator::isAtivo).isPresent()) {
            throw new RegraNegocioException(HttpStatus.CONFLICT, "O segundo fator desta conta já está cadastrado.");
        }
        Instant agora = clock.instant();
        String segredo = Totp.gerarSegredo();
        byte[] cifrado = cifra.cifrar(segredo, dadoAssociado(usuario.getId()));
        atual.ifPresentOrElse(
                s -> s.trocarSegredo(cifrado, agora),
                () -> repository.save(new SegundoFator(usuario.getId(), cifrado, agora)));
        return new Cadastro(segredo, Totp.uri(props.emissor(), usuario.getEmail(), segredo));
    }

    /**
     * Confere o código do aplicativo (ou um código de recuperação). O primeiro código certo depois do
     * cadastro ativa o segundo fator e gera os códigos de recuperação.
     */
    @Transactional
    public Verificacao verificar(Usuario usuario, String codigo, String codigoRecuperacao) {
        Optional<SegundoFator> registro = repository.travar(usuario.getId());
        if (registro.isEmpty()) {
            return Verificacao.INVALIDA;
        }
        SegundoFator fator = registro.get();
        Instant agora = clock.instant();
        if (codigoRecuperacao != null && !codigoRecuperacao.isBlank()) {
            if (!fator.isAtivo()) {
                return Verificacao.INVALIDA;
            }
            return codigos.pendente(usuario.getId(), TokenSeguro.hash(normalizar(codigoRecuperacao)))
                    .map(c -> {
                        c.marcarUsado(agora);
                        return new Verificacao(ResultadoLogin.CODIGO_RECUPERACAO, List.of());
                    })
                    .orElse(Verificacao.INVALIDA);
        }
        String segredo = cifra.decifrar(fator.getSegredo(), dadoAssociado(usuario.getId()));
        Long ultimo = props.rejeitarReuso() ? fator.getUltimoPasso() : null;
        OptionalLong passo = Totp.verificar(segredo, codigo, agora, ultimo);
        if (passo.isEmpty()) {
            return Verificacao.INVALIDA;
        }
        if (fator.isAtivo()) {
            fator.registrarPasso(passo.getAsLong());
            return new Verificacao(ResultadoLogin.SUCESSO, List.of());
        }
        fator.ativar(passo.getAsLong(), agora);
        return new Verificacao(ResultadoLogin.SEGUNDO_FATOR_CADASTRADO, novosCodigos(usuario.getId(), agora));
    }

    /** Segundo fator já ativo com um segredo conhecido: só no seed da stack de e2e. */
    @Transactional
    public void ativarComSegredo(Long usuarioId, String segredo) {
        if (repository.findById(usuarioId).isPresent()) {
            return;
        }
        Instant agora = clock.instant();
        SegundoFator fator = new SegundoFator(usuarioId, cifra.cifrar(segredo, dadoAssociado(usuarioId)), agora);
        fator.ativar(Totp.passo(agora) - 2, agora);
        repository.save(fator);
    }

    /** Apaga o segundo fator (comando redefinir-2fa): o próximo login pede o cadastro de novo. */
    @Transactional
    public boolean redefinir(Long usuarioId) {
        codigos.apagarDoUsuario(usuarioId);
        return repository
                .findById(usuarioId)
                .map(s -> {
                    repository.delete(s);
                    return true;
                })
                .orElse(false);
    }

    private List<String> novosCodigos(Long usuarioId, Instant agora) {
        codigos.apagarDoUsuario(usuarioId);
        List<String> gerados = new ArrayList<>();
        for (int i = 0; i < CODIGOS_RECUPERACAO; i++) {
            String codigo = codigoRecuperacao();
            gerados.add(codigo);
            codigos.save(new CodigoRecuperacao(usuarioId, TokenSeguro.hash(codigo), agora));
        }
        return gerados;
    }

    /** Dez caracteres sem os que se confundem (0/o, 1/l/i), no formato xxxxx-xxxxx. */
    static String codigoRecuperacao() {
        StringBuilder codigo = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                codigo.append('-');
            }
            codigo.append(ALFABETO_RECUPERACAO.charAt(ALEATORIO.nextInt(ALFABETO_RECUPERACAO.length())));
        }
        return codigo.toString();
    }

    static String normalizar(String codigo) {
        String limpo = codigo.replaceAll("[\\s-]", "").toLowerCase(Locale.ROOT);
        return limpo.length() == 10 ? limpo.substring(0, 5) + "-" + limpo.substring(5) : limpo;
    }

    private static String dadoAssociado(Long usuarioId) {
        return "segundo-fator:" + usuarioId;
    }

    /** Segredo em Base32 (para digitar) e o endereço otpauth:// do QR code. */
    public record Cadastro(String segredo, String uri) {}

    /** {@code resultado} nulo = código inválido; os códigos de recuperação só vêm na ativação. */
    public record Verificacao(ResultadoLogin resultado, List<String> codigosRecuperacao) {

        static final Verificacao INVALIDA = new Verificacao(null, List.of());

        public boolean valida() {
            return resultado != null;
        }
    }
}
