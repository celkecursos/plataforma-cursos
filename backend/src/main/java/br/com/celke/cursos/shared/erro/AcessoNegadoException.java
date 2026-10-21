package br.com.celke.cursos.shared.erro;

import org.springframework.http.HttpStatus;

/** Usuário autenticado sem direito ao recurso pela regra de negócio (ex.: sem matrícula vigente). */
public class AcessoNegadoException extends RegraNegocioException {

    public AcessoNegadoException(String mensagem) {
        super(HttpStatus.FORBIDDEN, mensagem);
    }
}
