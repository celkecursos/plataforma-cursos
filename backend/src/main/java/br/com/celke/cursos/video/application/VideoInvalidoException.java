package br.com.celke.cursos.video.application;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import org.springframework.http.HttpStatus;

/** Envio de vídeo recusado (formato, tamanho ou conteúdo). */
public class VideoInvalidoException extends RegraNegocioException {

    public VideoInvalidoException(String mensagem) {
        super(HttpStatus.BAD_REQUEST, mensagem);
    }
}
