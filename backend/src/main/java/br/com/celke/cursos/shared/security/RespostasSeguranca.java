package br.com.celke.cursos.shared.security;

import br.com.celke.cursos.shared.web.IdRequisicao;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
        escrever(request, response, HttpStatus.UNAUTHORIZED, "Autenticação necessária.");
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        escrever(request, response, HttpStatus.FORBIDDEN, "Acesso negado.");
    }

    private void escrever(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detalhe)
            throws IOException {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("title", status.getReasonPhrase());
        corpo.put("status", status.value());
        corpo.put("detail", detalhe);
        corpo.put("instance", request.getRequestURI());
        corpo.put("requestId", MDC.get(IdRequisicao.CHAVE_MDC));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), corpo);
    }
}
