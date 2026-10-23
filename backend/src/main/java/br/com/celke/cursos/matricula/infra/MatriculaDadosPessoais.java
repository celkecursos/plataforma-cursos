package br.com.celke.cursos.matricula.infra;

import br.com.celke.cursos.shared.lgpd.DadosPessoais;
import br.com.celke.cursos.shared.lgpd.Linhas;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Matrículas. Na exclusão da conta ficam (ligadas à conta anonimizada): são o registro do serviço
 * contratado e, nas pagas, parte da prova da venda.
 */
@Component
public class MatriculaDadosPessoais implements DadosPessoais {

    private final JdbcClient jdbc;

    public MatriculaDadosPessoais(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String secao() {
        return "matriculas";
    }

    @Override
    public List<Map<String, Object>> exportar(Long usuarioId) {
        return Linhas.de(jdbc.sql("""
                        SELECT m.id, c.titulo AS curso, t.nome AS turma, m.inicio, m.fim, m.status,
                               m.forma_pagamento, m.arquivada_em, m.cancelada_em, m.criado_em
                          FROM matriculas m
                          JOIN turmas t ON t.id = m.turma_id
                          JOIN cursos c ON c.id = t.curso_id
                         WHERE m.usuario_id = ? ORDER BY m.id
                        """).param(usuarioId).query().listOfRows());
    }
}
