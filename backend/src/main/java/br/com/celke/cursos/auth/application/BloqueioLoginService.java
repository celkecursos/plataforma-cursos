package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Bloqueio temporário depois de falhas de login seguidas, em dois níveis:
 *
 * <ul>
 *   <li>e-mail + IP: barra rápido quem tenta adivinhar a senha, sem impedir o dono da conta de
 *       entrar de outro lugar;
 *   <li>e-mail, de qualquer IP: limite maior, contra tentativas distribuídas em muitos IPs.
 * </ul>
 *
 * Os contadores ficam em memória (a API roda numa instância só) e zeram com um login correto ou
 * depois da janela sem novas falhas.
 */
@Service
@EnableConfigurationProperties(LimitesProperties.class)
public class BloqueioLoginService {

    private static final Logger LOG = LoggerFactory.getLogger(BloqueioLoginService.class);

    private final LimitesProperties props;
    private final Clock clock;
    private final Cache<String, Contador> contadores;

    public BloqueioLoginService(LimitesProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
        // Só limita a memória; a validade de cada contador é decidida pelo relógio da aplicação
        this.contadores = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterAccess(props.janelaFalhas().plus(props.bloqueio()))
                .build();
    }

    /** Recusa a tentativa se o par e-mail + IP ou o e-mail estiver bloqueado. */
    public void verificar(String email, String ip) {
        Instant agora = clock.instant();
        Duration espera = maior(esperaDe(chaveEmailIp(email, ip), agora), esperaDe(chaveEmail(email), agora));
        if (!espera.isZero()) {
            throw new MuitasTentativasException(espera);
        }
    }

    public void registrarFalha(String email, String ip) {
        Instant agora = clock.instant();
        contar(chaveEmailIp(email, ip), props.falhasPorEmailEIp(), agora);
        contar(chaveEmail(email), props.falhasPorEmail(), agora);
    }

    public void registrarSucesso(String email, String ip) {
        contadores.invalidate(chaveEmailIp(email, ip));
        contadores.invalidate(chaveEmail(email));
    }

    private void contar(String chave, int limite, Instant agora) {
        Contador contador = contadores.asMap().compute(chave, (k, atual) -> {
            Contador base = atual == null || atual.expirou(agora, props.janelaFalhas()) ? Contador.NOVO : atual;
            int falhas = base.falhas() + 1;
            if (falhas >= limite) {
                return new Contador(0, agora, agora.plus(props.bloqueio()));
            }
            return new Contador(falhas, agora, base.bloqueadoAte());
        });
        // O contador só volta a zero quando o limite é atingido e o bloqueio começa
        if (contador.falhas() == 0) {
            LOG.warn("Login bloqueado por {} ({} falhas seguidas)", props.bloqueio(), limite);
        }
    }

    private Duration esperaDe(String chave, Instant agora) {
        Contador contador = contadores.getIfPresent(chave);
        if (contador == null || contador.bloqueadoAte() == null || !agora.isBefore(contador.bloqueadoAte())) {
            return Duration.ZERO;
        }
        return Duration.between(agora, contador.bloqueadoAte());
    }

    private static Duration maior(Duration a, Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private static String chaveEmail(String email) {
        return "email:" + email.strip().toLowerCase(Locale.ROOT);
    }

    private static String chaveEmailIp(String email, String ip) {
        return chaveEmail(email) + "|ip:" + ip;
    }

    /** Falhas desde o último bloqueio ou sucesso; {@code bloqueadoAte} nulo quando não há bloqueio. */
    private record Contador(int falhas, Instant ultimaFalha, Instant bloqueadoAte) {

        static final Contador NOVO = new Contador(0, null, null);

        /** Sem falhas recentes e sem bloqueio em vigor: o contador recomeça. */
        boolean expirou(Instant agora, Duration janela) {
            boolean semFalhaRecente = ultimaFalha == null || !agora.isBefore(ultimaFalha.plus(janela));
            boolean semBloqueio = bloqueadoAte == null || !agora.isBefore(bloqueadoAte);
            return semFalhaRecente && semBloqueio;
        }
    }
}
