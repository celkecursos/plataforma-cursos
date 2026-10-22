package br.com.celke.cursos.shared.fila;

/**
 * Processa os jobs de um tipo. Exceção lançada = falha com nova tentativa (até o limite);
 * {@link ErroPermanenteException} = falha sem nova tentativa.
 */
public interface ManipuladorJob {

    String tipo();

    void processar(Job job) throws Exception;

    /**
     * O job foi para ERRO e não será tentado de novo (erro permanente, tentativas esgotadas ou worker
     * caindo no meio repetidas vezes). Permite registrar a falha no que o job processava.
     */
    default void desistir(Job job, String motivo) {}
}
