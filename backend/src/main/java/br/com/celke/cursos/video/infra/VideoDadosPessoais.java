package br.com.celke.cursos.video.infra;

import br.com.celke.cursos.shared.lgpd.DadosPessoais;
import br.com.celke.cursos.shared.lgpd.Linhas;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Acessos aos vídeos. Na exclusão da conta ficam até a retenção apagá-los (6 meses, prazo do Marco Civil
 * para os registros de acesso a aplicações).
 */
@Component
public class VideoDadosPessoais implements DadosPessoais {

    private final JdbcClient jdbc;

    public VideoDadosPessoais(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String secao() {
        return "acessosVideo";
    }

    @Override
    public List<Map<String, Object>> exportar(Long usuarioId) {
        return Linhas.de(jdbc.sql("""
                        SELECT v.aula_id, a.titulo AS aula, v.acessado_em, v.ip, v.user_agent AS navegador
                          FROM acessos_video v JOIN aulas a ON a.id = v.aula_id
                         WHERE v.usuario_id = ? ORDER BY v.id
                        """).param(usuarioId).query().listOfRows());
    }
}
