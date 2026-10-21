package br.com.celke.cursos.shared.erro;

import br.com.celke.cursos.shared.web.IdRequisicao;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converte toda exceção em ProblemDetail (RFC 9457). Erros do Spring MVC (404, 405, JSON inválido...)
 * vêm da classe base; aqui ficam validação, regra de negócio e o erro inesperado, que nunca expõe a
 * causa ao cliente.
 */
@RestControllerAdvice
public class ManipuladorErros extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ManipuladorErros.class);

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErroCampo> erros = ex.getBindingResult().getFieldErrors().stream()
                .map(erro -> new ErroCampo(erro.getField(), erro.getDefaultMessage()))
                .sorted(Comparator.comparing(ErroCampo::campo).thenComparing(ErroCampo::mensagem))
                .toList();
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dados inválidos.");
        problema.setProperty("erros", erros);
        return handleExceptionInternal(ex, problema, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(RegraNegocioException.class)
    ResponseEntity<Object> regraNegocio(RegraNegocioException ex, WebRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        HttpHeaders headers = new HttpHeaders();
        if (ex instanceof MuitasTentativasException limite) {
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(limite.segundosDeEspera()));
        }
        return handleExceptionInternal(ex, problema, headers, ex.getStatus(), request);
    }

    /**
     * Erro inesperado: registra a causa no log e devolve só o id da requisição. Exceções de
     * segurança voltam para o Spring Security, que decide entre 401 e 403.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> inesperado(Exception ex, WebRequest request) throws Exception {
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        LOG.error("Erro inesperado", ex);
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno. Se o problema continuar, informe o código " + MDC.get(IdRequisicao.CHAVE_MDC) + ".");
        return handleExceptionInternal(ex, problema, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    /** Completa todo ProblemDetail com o caminho da requisição e o id de correlação. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problema) {
            if (request instanceof ServletWebRequest servlet) {
                HttpServletRequest http = servlet.getRequest();
                problema.setInstance(URI.create(http.getRequestURI()));
            }
            problema.setProperty("requestId", MDC.get(IdRequisicao.CHAVE_MDC));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }
}
