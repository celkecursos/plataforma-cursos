package br.com.celke.cursos.termo.infra;

import br.com.celke.cursos.shared.lgpd.DadosPessoais;
import br.com.celke.cursos.shared.lgpd.Linhas;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Aceites dos termos de uso e da política de privacidade. Na exclusão da conta ficam: são a prova do
 * consentimento (LGPD, art. 8º, § 2º).
 */
@Component
public class TermoDadosPessoais implements DadosPessoais {

    private final JdbcClient jdbc;

    public TermoDadosPessoais(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String secao() {
        return "aceitesTermos";
    }

    @Override
    public List<Map<String, Object>> exportar(Long usuarioId) {
        return Linhas.de(jdbc.sql("""
                        SELECT t.tipo, t.versao, a.aceito_em, a.ip, a.user_agent AS navegador
                          FROM aceites_termos a JOIN termos t ON t.id = a.termo_id
                         WHERE a.usuario_id = ? ORDER BY a.id
                        """).param(usuarioId).query().listOfRows());
    }
}
