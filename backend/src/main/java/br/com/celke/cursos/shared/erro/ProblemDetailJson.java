package br.com.celke.cursos.shared.erro;

import br.com.celke.cursos.shared.web.IdRequisicao;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escreve um ProblemDetail direto na resposta, para erros que nascem em filtros, antes do Spring
 * MVC (segurança, limite de requisições). Mesmo formato do {@link ManipuladorErros}.
 */
public final class ProblemDetailJson {

    private ProblemDetailJson() {}

    public static void escrever(
            JsonMapper jsonMapper,
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String detalhe)
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
