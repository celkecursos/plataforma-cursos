package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import org.springframework.http.HttpStatus;

/** Refresh token ausente, expirado, revogado ou reutilizado: é preciso entrar de novo. */
public class SessaoInvalidaException extends RegraNegocioException {

    public SessaoInvalidaException() {
        super(HttpStatus.UNAUTHORIZED, "Sessão expirada. Entre novamente.");
    }
}
