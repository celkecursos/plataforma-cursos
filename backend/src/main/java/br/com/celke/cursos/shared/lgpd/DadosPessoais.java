package br.com.celke.cursos.shared.lgpd;

import java.util.List;
import java.util.Map;

/**
 * Dados pessoais que um módulo guarda de um usuário, para os direitos do titular (LGPD, art. 18):
 * exportar tudo e excluir a conta. Cada módulo implementa para as próprias tabelas; quem pede não
 * conhece as tabelas de ninguém.
 */
public interface DadosPessoais {

    /** Nome da seção na exportação (ex.: {@code matriculas}). */
    String secao();

    /** Registros do usuário, em ordem cronológica. */
    List<Map<String, Object>> exportar(Long usuarioId);

    /**
     * Na exclusão da conta: apaga o que não precisa ficar e anonimiza o que a lei manda guardar. O padrão
     * é manter (registros com prazo legal de guarda, que a retenção apaga depois).
     */
    default void excluir(Long usuarioId) {}
}
