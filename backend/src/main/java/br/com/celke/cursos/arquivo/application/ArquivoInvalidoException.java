package br.com.celke.cursos.arquivo.application;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import org.springframework.http.HttpStatus;

/** Arquivo de apoio recusado (formato, tamanho, conteúdo ou envio inválido). */
public class ArquivoInvalidoException extends RegraNegocioException {

    public ArquivoInvalidoException(String mensagem) {
        super(HttpStatus.BAD_REQUEST, mensagem);
    }
}
