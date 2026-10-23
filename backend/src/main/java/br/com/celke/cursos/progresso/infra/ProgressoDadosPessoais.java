package br.com.celke.cursos.progresso.infra;

import br.com.celke.cursos.shared.lgpd.DadosPessoais;
import br.com.celke.cursos.shared.lgpd.Linhas;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Progresso nas aulas. Na exclusão da conta, apagado. */
@Component
public class ProgressoDadosPessoais implements DadosPessoais {

    private final JdbcClient jdbc;

    public ProgressoDadosPessoais(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String secao() {
        return "progresso";
    }

    @Override
    public List<Map<String, Object>> exportar(Long usuarioId) {
        return Linhas.de(jdbc.sql("""
                        SELECT p.aula_id, a.titulo AS aula, p.posicao_segundos, p.vista, p.vista_em, p.revisar,
                               p.atualizado_em
                          FROM progresso_aulas p JOIN aulas a ON a.id = p.aula_id
                         WHERE p.usuario_id = ? ORDER BY p.id
                        """).param(usuarioId).query().listOfRows());
    }

    @Override
    public void excluir(Long usuarioId) {
        jdbc.sql("DELETE FROM progresso_aulas WHERE usuario_id = ?")
                .param(usuarioId)
                .update();
    }
}
