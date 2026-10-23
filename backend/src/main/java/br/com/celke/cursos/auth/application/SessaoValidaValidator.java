package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.usuario.application.UsuarioService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Recusa o access token de conta bloqueada ou emitido antes do "encerrar sessões" do ADM, sem esperar
 * os 10 minutos de validade dele. A situação de cada conta fica 30 segundos em memória (o bloqueio
 * feito nesta instância vale na hora: ela esquece a conta).
 */
@Component
public class SessaoValidaValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error SESSAO_ENCERRADA = new OAuth2Error("invalid_token", "Sessão encerrada.", null);

    private final UsuarioService usuarioService;
    private final Cache<Long, Optional<Situacao>> situacoes = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofSeconds(30))
            .build();

    public SessaoValidaValidator(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Long id;
        try {
            id = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            return OAuth2TokenValidatorResult.failure(SESSAO_ENCERRADA);
        }
        Optional<Situacao> situacao = situacoes.get(id, this::carregar);
        boolean valida = situacao.filter(Situacao::ativo)
                .filter(s -> s.encerradasEm() == null
                        || jwt.getIssuedAt() == null
                        || !jwt.getIssuedAt().isBefore(s.encerradasEm().truncatedTo(ChronoUnit.SECONDS)))
                .isPresent();
        return valida ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(SESSAO_ENCERRADA);
    }

    /**
     * Esquece a situação guardada (depois de bloquear, desbloquear, encerrar as sessões ou excluir a
     * conta). Dentro de uma transação, esquece de novo depois do commit: uma requisição no meio do caminho
     * não deixa a situação antiga guardada.
     */
    public void esquecer(Long usuarioId) {
        situacoes.invalidate(usuarioId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    situacoes.invalidate(usuarioId);
                }
            });
        }
    }

    private Optional<Situacao> carregar(Long id) {
        return usuarioService.buscarPorId(id).map(u -> new Situacao(u.isAtivo(), u.getSessoesEncerradasEm()));
    }

    private record Situacao(boolean ativo, Instant encerradasEm) {}
}
