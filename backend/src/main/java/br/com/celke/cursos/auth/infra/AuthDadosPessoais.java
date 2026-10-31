package br.com.celke.cursos.auth.infra;

import br.com.celke.cursos.shared.lgpd.DadosPessoais;
import br.com.celke.cursos.shared.lgpd.Linhas;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Entradas na conta (auditoria de login: guardada pelo prazo do Marco Civil e apagada pela retenção).
 * Na exclusão, saem as sessões, os links de e-mail e o segundo fator.
 */
@Component
public class AuthDadosPessoais implements DadosPessoais {

    private final JdbcClient jdbc;

    public AuthDadosPessoais(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String secao() {
        return "entradas";
    }

    @Override
    public List<Map<String, Object>> exportar(Long usuarioId) {
        return Linhas.de(jdbc.sql("""
                        SELECT criado_em AS quando, resultado, ip, user_agent AS navegador
                          FROM auditoria_login WHERE usuario_id = ? ORDER BY id
                        """).param(usuarioId).query().listOfRows());
    }

    @Override
    public void excluir(Long usuarioId) {
        jdbc.sql("DELETE FROM refresh_tokens WHERE usuario_id = ?")
                .param(usuarioId)
                .update();
        jdbc.sql("DELETE FROM tokens_uso_unico WHERE usuario_id = ?")
                .param(usuarioId)
                .update();
        jdbc.sql("DELETE FROM codigos_recuperacao WHERE usuario_id = ?")
                .param(usuarioId)
                .update();
        jdbc.sql("DELETE FROM segundo_fator WHERE usuario_id = ?")
                .param(usuarioId)
                .update();
    }
}
