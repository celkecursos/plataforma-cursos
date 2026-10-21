package br.com.celke.cursos.shared.erro;

import org.springframework.http.HttpStatus;

/**
 * Erro de negócio com mensagem para o usuário. A mensagem vai no {@code detail} do ProblemDetail,
 * por isso deve ser escrita em português e nunca conter dados internos.
 */
public class RegraNegocioException extends RuntimeException {

    private final HttpStatus status;

    public RegraNegocioException(HttpStatus status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
