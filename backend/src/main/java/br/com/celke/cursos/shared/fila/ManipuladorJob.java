package br.com.celke.cursos.shared.fila;

/**
 * Processa os jobs de um tipo. Exceção lançada = falha com nova tentativa (até o limite);
 * {@link ErroPermanenteException} = falha sem nova tentativa.
 */
public interface ManipuladorJob {

    String tipo();

    void processar(Job job) throws Exception;
}
