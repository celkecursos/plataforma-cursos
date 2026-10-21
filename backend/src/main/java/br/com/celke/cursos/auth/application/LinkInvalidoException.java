package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import org.springframework.http.HttpStatus;

/** Link de e-mail inexistente, já usado ou expirado: mesma mensagem nos três casos. */
public class LinkInvalidoException extends RegraNegocioException {

    public LinkInvalidoException() {
        super(HttpStatus.BAD_REQUEST, "Link inválido ou expirado. Peça um novo.");
    }
}
