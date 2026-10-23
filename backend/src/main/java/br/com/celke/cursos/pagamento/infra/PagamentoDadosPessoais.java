package br.com.celke.cursos.pagamento.infra;

import br.com.celke.cursos.shared.lgpd.DadosPessoais;
import br.com.celke.cursos.shared.lgpd.Linhas;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Pedidos. Na exclusão da conta ficam (valor, status e datas são registro fiscal da venda), sem o
 * e-mail e o nome do comprador.
 */
@Component
public class PagamentoDadosPessoais implements DadosPessoais {

    private final JdbcClient jdbc;

    public PagamentoDadosPessoais(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String secao() {
        return "pedidos";
    }

    @Override
    public List<Map<String, Object>> exportar(Long usuarioId) {
        return Linhas.de(jdbc.sql("""
                        SELECT codigo, email, nome, status, valor_centavos, moeda, forma_pagamento, criado_em,
                               atualizado_em
                          FROM pedidos WHERE usuario_id = ? ORDER BY id
                        """).param(usuarioId).query().listOfRows());
    }

    @Override
    public void excluir(Long usuarioId) {
        jdbc.sql("UPDATE pedidos SET email = :email, nome = NULL WHERE usuario_id = :usuario")
                .param("email", "excluida-" + usuarioId + "@conta-excluida.invalid")
                .param("usuario", usuarioId)
                .update();
    }
}
