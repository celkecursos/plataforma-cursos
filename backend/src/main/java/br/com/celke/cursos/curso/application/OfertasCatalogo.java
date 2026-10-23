package br.com.celke.cursos.curso.application;

import java.util.List;

/**
 * Ofertas à venda de uma turma, mostradas no detalhe do catálogo. Implementada pelo módulo de
 * pagamento: o catálogo não conhece meios de pagamento.
 */
public interface OfertasCatalogo {

    /** Ofertas ativas da turma no meio de pagamento ativo; vazia se nada está à venda. */
    List<OfertaCatalogo> ativas(Long turmaId);

    record OfertaCatalogo(Long id, String titulo, long precoCentavos, String moeda) {}
}
