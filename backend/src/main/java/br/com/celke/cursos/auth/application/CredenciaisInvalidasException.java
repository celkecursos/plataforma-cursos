package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import org.springframework.http.HttpStatus;

/** Mesma mensagem para e-mail inexistente, senha errada e usuário inativo: não revela o motivo. */
public class CredenciaisInvalidasException extends RegraNegocioException {

    public CredenciaisInvalidasException() {
        super(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos.");
    }
}
