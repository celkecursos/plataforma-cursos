package br.com.celke.cursos.shared.fila;

/** Erro que não se resolve tentando de novo (ex.: dado inválido no payload). */
public class ErroPermanenteException extends RuntimeException {

    public ErroPermanenteException(String mensagem) {
        super(mensagem);
    }
}
