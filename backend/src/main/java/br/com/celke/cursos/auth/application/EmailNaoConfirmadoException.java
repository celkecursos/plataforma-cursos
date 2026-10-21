package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import org.springframework.http.HttpStatus;

/** Senha certa, mas o e-mail ainda não foi confirmado (só quem sabe a senha recebe esta resposta). */
public class EmailNaoConfirmadoException extends RegraNegocioException {

    public EmailNaoConfirmadoException() {
        super(HttpStatus.FORBIDDEN, "Confirme o seu e-mail antes de entrar. Se não recebeu o link, peça um novo.");
    }
}
