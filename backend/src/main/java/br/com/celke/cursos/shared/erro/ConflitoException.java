package br.com.celke.cursos.shared.erro;

import org.springframework.http.HttpStatus;

/** Operação que conflita com o estado atual (ex.: e-mail ou slug já cadastrado). */
public class ConflitoException extends RegraNegocioException {

    public ConflitoException(String mensagem) {
        super(HttpStatus.CONFLICT, mensagem);
    }
}
