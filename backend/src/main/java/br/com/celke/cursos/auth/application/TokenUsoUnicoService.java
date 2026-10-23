package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.domain.TipoToken;
import br.com.celke.cursos.auth.domain.TokenUsoUnico;
import br.com.celke.cursos.auth.infra.TokenUsoUnicoRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tokens dos links enviados por e-mail. Emitir um novo invalida os anteriores do mesmo tipo; cada
 * conta recebe no máximo {@code emailsPorHora} tokens de um tipo por hora.
 */
@Service
@EnableConfigurationProperties(ContaProperties.class)
public class TokenUsoUnicoService {

    private static final Logger LOG = LoggerFactory.getLogger(TokenUsoUnicoService.class);

    /** Tempo para digitar o código do aplicativo depois de acertar a senha. */
    static final Duration VALIDADE_DESAFIO = Duration.ofMinutes(5);

    private final TokenUsoUnicoRepository repository;
    private final ContaProperties props;
    private final Clock clock;

    public TokenUsoUnicoService(TokenUsoUnicoRepository repository, ContaProperties props, Clock clock) {
        this.repository = repository;
        this.props = props;
        this.clock = clock;
    }

    /** Devolve o token em texto, ou vazio se a conta já recebeu o máximo de e-mails na última hora. */
    @Transactional
    public Optional<String> emitir(Long usuarioId, TipoToken tipo) {
        Instant agora = clock.instant();
        long naUltimaHora =
                repository.countByUsuarioIdAndTipoAndCriadoEmAfter(usuarioId, tipo, agora.minus(Duration.ofHours(1)));
        if (tipo.porEmail() && naUltimaHora >= props.emailsPorHora()) {
            LOG.warn("Limite de e-mails de {} atingido para o usuário {}", tipo, usuarioId);
            return Optional.empty();
        }
        repository.invalidarPendentes(usuarioId, tipo, agora);
        String token = TokenSeguro.gerar();
        repository.save(new TokenUsoUnico(usuarioId, tipo, TokenSeguro.hash(token), agora.plus(validade(tipo)), agora));
        return Optional.of(token);
    }

    /** Dono de um token ainda válido, sem usá-lo (para conferir regras antes de consumir); trava o token. */
    @Transactional
    public Optional<Long> dono(String token, TipoToken tipo) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Instant agora = clock.instant();
        return repository
                .findByHash(TokenSeguro.hash(token))
                .filter(t -> t.podeSerUsado(tipo, agora))
                .map(TokenUsoUnico::getUsuarioId);
    }

    /** Usa o token e devolve o id do dono; vazio se o token não existe, já foi usado, expirou ou é de outro tipo. */
    @Transactional
    public Optional<Long> consumir(String token, TipoToken tipo) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Instant agora = clock.instant();
        return repository
                .findByHash(TokenSeguro.hash(token))
                .filter(t -> t.podeSerUsado(tipo, agora))
                .map(t -> {
                    t.marcarUsado(agora);
                    return t.getUsuarioId();
                });
    }

    public Duration validade(TipoToken tipo) {
        return switch (tipo) {
            case CONFIRMACAO_EMAIL -> props.validadeConfirmacao();
            case RECUPERACAO_SENHA -> props.validadeRecuperacao();
            case DESAFIO_SEGUNDO_FATOR -> VALIDADE_DESAFIO;
        };
    }
}
