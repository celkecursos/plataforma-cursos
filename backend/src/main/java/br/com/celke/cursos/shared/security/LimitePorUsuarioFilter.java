package br.com.celke.cursos.shared.security;

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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Limite por usuário logado nas gravações da API. Roda depois da segurança (já sabe quem é o usuário
 * pelo JWT); requisição anônima não passa por aqui (as rotas anônimas têm o limite por IP). O checkout
 * tem um limite próprio, menor: cada um abre um pedido no meio de pagamento.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(LimitesUsuarioProperties.class)
public class LimitePorUsuarioFilter extends OncePerRequestFilter {

    static final String CHECKOUT = "/api/pagamentos/checkout";
    private static final Set<String> METODOS_ESCRITA = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Logger LOG = LoggerFactory.getLogger(LimitePorUsuarioFilter.class);

    private final LimitesUsuarioProperties props;
    private final JsonMapper jsonMapper;
    private final TimeMeter relogio;
    private final Cache<String, Bucket> baldes = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofHours(2))
            .build();

    public LimitePorUsuarioFilter(LimitesUsuarioProperties props, JsonMapper jsonMapper, Clock clock) {
        this.props = props;
        this.jsonMapper = jsonMapper;
        this.relogio = new RelogioBucket(clock);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !METODOS_ESCRITA.contains(request.getMethod())
                || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (!(autenticacao instanceof JwtAuthenticationToken jwt)) {
            chain.doFilter(request, response);
            return;
        }
        String usuario = jwt.getToken().getSubject();
        if (!consumir("escrita:" + usuario, props.escritasPorMinuto(), Duration.ofMinutes(1), request, response)) {
            return;
        }
        if (CHECKOUT.equals(request.getRequestURI())
                && !consumir("checkout:" + usuario, props.checkoutsPorHora(), Duration.ofHours(1), request, response)) {
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean consumir(
            String chave, int limite, Duration periodo, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        ConsumptionProbe consumo =
                baldes.get(chave, c -> balde(limite, periodo)).tryConsumeAndReturnRemaining(1);
        if (consumo.isConsumed()) {
            return true;
        }
        MuitasTentativasException limiteAtingido =
                new MuitasTentativasException(Duration.ofNanos(consumo.getNanosToWaitForRefill()));
        LOG.warn(
                "Limite por usuário atingido ({}) em {} {}",
                chave.substring(0, chave.indexOf(':')),
                request.getMethod(),
                request.getRequestURI());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(limiteAtingido.segundosDeEspera()));
        ProblemDetailJson.escrever(
                jsonMapper, request, response, HttpStatus.TOO_MANY_REQUESTS, limiteAtingido.getMessage());
        return false;
    }

    private Bucket balde(int limite, Duration periodo) {
        return Bucket.builder()
                .addLimit(l -> l.capacity(limite).refillGreedy(limite, periodo))
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
