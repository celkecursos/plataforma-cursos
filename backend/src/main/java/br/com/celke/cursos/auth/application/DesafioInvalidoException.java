package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import org.springframework.http.HttpStatus;

/** Desafio do segundo fator inexistente, usado ou vencido (5 minutos depois da senha). */
public class DesafioInvalidoException extends RegraNegocioException {

    public DesafioInvalidoException() {
        super(HttpStatus.UNAUTHORIZED, "O tempo para informar o código acabou. Entre de novo.");
    }
}
