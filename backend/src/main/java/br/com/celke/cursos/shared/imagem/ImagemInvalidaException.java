package br.com.celke.cursos.shared.imagem;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import org.springframework.http.HttpStatus;

/** Arquivo que não é uma imagem aceita (formato, tamanho ou dimensões). */
public class ImagemInvalidaException extends RegraNegocioException {

    public ImagemInvalidaException(String mensagem) {
        super(HttpStatus.BAD_REQUEST, mensagem);
    }
}
