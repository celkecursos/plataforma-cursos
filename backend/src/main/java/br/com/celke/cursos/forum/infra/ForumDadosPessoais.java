package br.com.celke.cursos.forum.infra;

import br.com.celke.cursos.shared.lgpd.DadosPessoais;
import br.com.celke.cursos.shared.lgpd.Linhas;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Perguntas e respostas no fórum. Na exclusão da conta ficam, sem o autor (aparece "Conta excluída"):
 * fazem parte da conversa da turma, e as respostas dos outros dependem delas.
 */
@Component
public class ForumDadosPessoais implements DadosPessoais {

    private final JdbcClient jdbc;

    public ForumDadosPessoais(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String secao() {
        return "forum";
    }

    @Override
    public List<Map<String, Object>> exportar(Long usuarioId) {
        return Linhas.de(jdbc.sql("""
                        SELECT 'pergunta' AS tipo, id, titulo, corpo_markdown AS texto, criado_em
                          FROM forum_topicos WHERE autor_id = :usuario
                        UNION ALL
                        SELECT 'resposta', id, NULL, corpo_markdown, criado_em
                          FROM forum_respostas WHERE autor_id = :usuario
                         ORDER BY criado_em, id
                        """).param("usuario", usuarioId).query().listOfRows());
    }
}
