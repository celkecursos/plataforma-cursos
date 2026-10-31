package br.com.celke.cursos.certificado.infra;

import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.lgpd.DadosPessoais;
import br.com.celke.cursos.shared.lgpd.Linhas;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Certificados emitidos. Na exclusão da conta, apagados com o PDF: o código deixa de validar (o nome
 * completo só existe neles).
 */
@Component
public class CertificadoDadosPessoais implements DadosPessoais {

    private final JdbcClient jdbc;
    private final StorageService storage;

    public CertificadoDadosPessoais(JdbcClient jdbc, StorageService storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    @Override
    public String secao() {
        return "certificados";
    }

    @Override
    public List<Map<String, Object>> exportar(Long usuarioId) {
        return Linhas.de(jdbc.sql("""
                        SELECT codigo, nome_completo, curso_titulo, turma_nome, carga_horaria_horas,
                               percentual_concluido, emitido_em
                          FROM certificados WHERE usuario_id = ? ORDER BY id
                        """).param(usuarioId).query().listOfRows());
    }

    @Override
    public void excluir(Long usuarioId) {
        List<String> pdfs = jdbc.sql(
                        "SELECT pdf_chave FROM certificados WHERE usuario_id = ? AND pdf_chave IS NOT NULL")
                .param(usuarioId)
                .query(String.class)
                .list();
        jdbc.sql("DELETE FROM certificados WHERE usuario_id = ?")
                .param(usuarioId)
                .update();
        pdfs.forEach(storage::excluir);
    }
}
