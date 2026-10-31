package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.shared.erro.CampoInvalidoException;

/** Código do aplicativo ou de recuperação que não confere. */
public class CodigoInvalidoException extends CampoInvalidoException {

    public CodigoInvalidoException() {
        super("codigo", "Código inválido. Confira o código no aplicativo e tente de novo.");
    }
}
