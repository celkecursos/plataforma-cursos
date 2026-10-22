package br.com.celke.cursos.shared.auditoria;

import br.com.celke.cursos.shared.web.IdRequisicao;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Quem fez a mudança e de onde: o usuário do token e o job da fila em processamento ({@link #CHAVE_JOB}
 * no MDC), ou a requisição (rota, IP, user-agent, id de correlação), ou "sistema" (agendamentos).
 *
 * @param usuarioId nulo sem usuário logado
 */
public record ContextoAuditoria(Long usuarioId, String origem, String ip, String userAgent, String requestId) {

    /** Chave do MDC com o tipo do job em processamento (posta pelo processador da fila). */
    public static final String CHAVE_JOB = "job";

    static final int TAMANHO_MAXIMO_ORIGEM = 200;

    /** Contexto da thread atual. */
    public static ContextoAuditoria atual() {
        Long usuarioId = usuario(SecurityContextHolder.getContext().getAuthentication());
        String requestId = MDC.get(IdRequisicao.CHAVE_MDC);
        String job = MDC.get(CHAVE_JOB);
        if (job != null) {
            return new ContextoAuditoria(usuarioId, "job " + job, null, null, requestId);
        }
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
            HttpServletRequest request = atributos.getRequest();
            OrigemRequisicao origem = OrigemRequisicao.de(request);
            return new ContextoAuditoria(usuarioId, rota(request), origem.ip(), origem.userAgent(), requestId);
        }
        return new ContextoAuditoria(usuarioId, "sistema", null, null, requestId);
    }

    /** Método e rota com as variáveis ({@code PUT /api/adm/cursos/{id}}); sem rota mapeada, o caminho. */
    static String rota(HttpServletRequest request) {
        Object padrao = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String caminho = padrao == null ? request.getRequestURI() : padrao.toString();
        String rota = request.getMethod() + " " + caminho;
        return rota.length() <= TAMANHO_MAXIMO_ORIGEM ? rota : rota.substring(0, TAMANHO_MAXIMO_ORIGEM);
    }

    private static Long usuario(Authentication autenticacao) {
        if (autenticacao instanceof JwtAuthenticationToken jwt) {
            try {
                return Long.valueOf(jwt.getToken().getSubject());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
