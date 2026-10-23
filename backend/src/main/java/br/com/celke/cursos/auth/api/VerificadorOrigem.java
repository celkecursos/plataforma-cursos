package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.auth.application.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Proteção contra CSRF nas rotas que agem só pelo cookie (refresh e logout). O {@code SameSite=Strict}
 * não basta: outras aplicações em subdomínios do mesmo domínio contam como o mesmo site para o
 * navegador. Por isso a origem também é conferida:
 *
 * <ul>
 *   <li>{@code Sec-Fetch-Site: cross-site} é recusado;
 *   <li>{@code Origin} presente precisa estar na lista de origens permitidas ({@code null} é recusado);
 *   <li>sem {@code Origin}, a requisição não veio de um navegador (que sempre envia em POST) e passa.
 * </ul>
 */
@Component
public class VerificadorOrigem {

    private static final Logger LOG = LoggerFactory.getLogger(VerificadorOrigem.class);

    private final AuthProperties props;

    public VerificadorOrigem(AuthProperties props) {
        this.props = props;
    }

    public void verificar(HttpServletRequest request) {
        String site = request.getHeader("Sec-Fetch-Site");
        if ("cross-site".equalsIgnoreCase(site)) {
            recusar(request, "Sec-Fetch-Site: cross-site");
        }
        String origem = request.getHeader("Origin");
        if (origem == null) {
            return;
        }
        if (!props.origensPermitidas().contains(AuthProperties.normalizarOrigem(origem))) {
            recusar(request, "Origin: " + origem);
        }
    }

    private static void recusar(HttpServletRequest request, String motivo) {
        LOG.warn("Requisição recusada em {} ({})", request.getRequestURI(), motivo);
        throw new OrigemNaoPermitidaException();
    }
}
