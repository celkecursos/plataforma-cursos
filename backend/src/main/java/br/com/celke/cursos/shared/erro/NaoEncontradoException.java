package br.com.celke.cursos.shared.erro;

import org.springframework.http.HttpStatus;

/** Recurso inexistente ou de outro usuário (responder 404 nos dois casos não revela o que existe). */
public class NaoEncontradoException extends RegraNegocioException {

    public NaoEncontradoException(String mensagem) {
        super(HttpStatus.NOT_FOUND, mensagem);
    }
}
