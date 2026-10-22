package br.com.celke.cursos.termo.api;

import br.com.celke.cursos.termo.application.TermoPendenteException;
import br.com.celke.cursos.termo.application.TermoService;
import br.com.celke.cursos.termo.domain.Termo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Bloqueia a área do aluno e a entrega de vídeo enquanto houver termo vigente sem aceite (primeiro
 * acesso ou versão nova): 403 com a lista dos termos pendentes. Perfil, sessão e as próprias rotas de
 * termos continuam liberados, para o aceite ser possível.
 */
public class TermoPendenteInterceptor implements HandlerInterceptor {

    private final TermoService termoService;

    public TermoPendenteInterceptor(TermoService termoService) {
        this.termoService = termoService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao instanceof JwtAuthenticationToken jwt) {
            List<Termo> pendentes =
                    termoService.pendentes(Long.valueOf(jwt.getToken().getSubject()));
            if (!pendentes.isEmpty()) {
                throw new TermoPendenteException(pendentes);
            }
        }
        return true;
    }
}
