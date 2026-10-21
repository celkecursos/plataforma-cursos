package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.domain.RefreshToken;
import br.com.celke.cursos.auth.infra.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh tokens opacos com rotação: cada uso marca o token como usado e emite outro na mesma
 * família. Um token já usado que reaparece indica roubo; a família inteira é revogada.
 */
@Service
public class RefreshTokenService {

    private static final Logger LOG = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final AuthProperties props;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, AuthProperties props, Clock clock) {
        this.repository = repository;
        this.props = props;
        this.clock = clock;
    }

    /** Abre uma família nova (login). Devolve o token em texto, que só existe no cookie. */
    @Transactional
    public RefreshEmitido emitir(Long usuarioId) {
        return emitirNaFamilia(usuarioId, UUID.randomUUID(), clock.instant());
    }

    /**
     * Troca o token por um novo da mesma família. Vazio quando o token é desconhecido, expirado,
     * revogado ou reutilizado (neste caso a família é revogada).
     */
    @Transactional
    public Optional<RefreshEmitido> rotacionar(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Instant agora = clock.instant();
        Optional<RefreshToken> encontrado = repository.findByHash(hash(token));
        if (encontrado.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken atual = encontrado.get();
        if (atual.estaRevogado() || atual.estaExpirado(agora)) {
            return Optional.empty();
        }
        if (atual.foiUsado()) {
            LOG.warn(
                    "Refresh token reutilizado: família {} do usuário {} revogada",
                    atual.getFamilia(),
                    atual.getUsuarioId());
            repository.revogarFamilia(atual.getFamilia(), agora);
            return Optional.empty();
        }
        atual.marcarUsado(agora);
        return Optional.of(emitirNaFamilia(atual.getUsuarioId(), atual.getFamilia(), agora));
    }

    /** Logout: revoga a família do token (se ele existir). */
    @Transactional
    public void revogarFamiliaDoToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        repository.findByHash(hash(token)).ifPresent(t -> repository.revogarFamilia(t.getFamilia(), clock.instant()));
    }

    /** "Sair de todos os dispositivos": revoga todas as famílias do usuário. */
    @Transactional
    public void revogarTodas(Long usuarioId) {
        repository.revogarTodasDoUsuario(usuarioId, clock.instant());
    }

    private RefreshEmitido emitirNaFamilia(Long usuarioId, UUID familia, Instant agora) {
        byte[] aleatorio = new byte[32];
        ALEATORIO.nextBytes(aleatorio);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(aleatorio);
        Instant expiraEm = agora.plus(props.validadeRefresh());
        repository.save(new RefreshToken(usuarioId, familia, hash(token), expiraEm, agora));
        return new RefreshEmitido(token, usuarioId, expiraEm);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record RefreshEmitido(String token, Long usuarioId, Instant expiraEm) {}
}
