package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.auth.application.LimitesProperties;
import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import br.com.celke.cursos.shared.erro.ProblemDetailJson;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Limite de requisições por IP nas rotas de autenticação (token bucket do Bucket4j). Conta toda
 * requisição, até as inválidas, e roda antes da segurança e do Spring MVC: o excesso é barrado
 * sem calcular hash nem consultar o banco.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class LimitePorIpFilter extends OncePerRequestFilter {

    /** Rotas limitadas (sempre POST). */
    static final Set<String> ROTAS = Set.of("/api/auth/login", "/api/auth/refresh");

    static final String CABECALHO_RESTANTE = "X-RateLimit-Remaining";

    private static final Logger LOG = LoggerFactory.getLogger(LimitePorIpFilter.class);

    private final int limitePorMinuto;
    private final JsonMapper jsonMapper;
    private final TimeMeter relogio;
    private final Cache<String, Bucket> baldes = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    public LimitePorIpFilter(LimitesProperties props, JsonMapper jsonMapper, Clock clock) {
        this.limitePorMinuto = props.requisicoesPorIpPorMinuto();
        this.jsonMapper = jsonMapper;
        this.relogio = new RelogioBucket(clock);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !ROTAS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        ConsumptionProbe consumo = baldes.get(ip, chave -> novoBalde()).tryConsumeAndReturnRemaining(1);
        response.setHeader(CABECALHO_RESTANTE, String.valueOf(consumo.getRemainingTokens()));
        if (consumo.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        MuitasTentativasException limite =
                new MuitasTentativasException(Duration.ofNanos(consumo.getNanosToWaitForRefill()));
        LOG.warn("Limite de requisições por IP atingido em {}", request.getRequestURI());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(limite.segundosDeEspera()));
        ProblemDetailJson.escrever(jsonMapper, request, response, HttpStatus.TOO_MANY_REQUESTS, limite.getMessage());
    }

    private Bucket novoBalde() {
        return Bucket.builder()
                .addLimit(
                        limite -> limite.capacity(limitePorMinuto).refillGreedy(limitePorMinuto, Duration.ofMinutes(1)))
                .withCustomTimePrecision(relogio)
                .build();
    }

    /** Relógio do Bucket4j a partir do {@link Clock} da aplicação (testável). */
    record RelogioBucket(Clock clock) implements TimeMeter {

        @Override
        public long currentTimeNanos() {
            return Duration.ofMillis(clock.millis()).toNanos();
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
