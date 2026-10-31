package br.com.celke.cursos.shared.security;

import br.com.celke.cursos.shared.erro.ProblemDetailJson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Respostas 401 e 403 do Spring Security no mesmo formato ProblemDetail do resto da API. Essas
 * respostas nascem nos filtros, antes do Spring MVC, por isso são escritas aqui.
 */
public class RespostasSeguranca implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    public RespostasSeguranca(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        ProblemDetailJson.escrever(jsonMapper, request, response, HttpStatus.UNAUTHORIZED, "Autenticação necessária.");
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        ProblemDetailJson.escrever(jsonMapper, request, response, HttpStatus.FORBIDDEN, "Acesso negado.");
    }
}
