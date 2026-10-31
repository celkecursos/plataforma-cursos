package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import org.springframework.http.HttpStatus;

/** Requisição de navegador vinda de uma origem fora da lista permitida. */
public class OrigemNaoPermitidaException extends RegraNegocioException {

    public OrigemNaoPermitidaException() {
        super(HttpStatus.FORBIDDEN, "Origem não permitida.");
    }
}
