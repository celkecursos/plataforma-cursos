package br.com.celke.cursos.shared.banco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.support.IntegrationTest;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * O usuário da aplicação (criado pelo afterMigrate.sql) lê e grava dados, mas não cria, altera nem apaga
 * tabelas, nem toca no histórico das migrations.
 */
class UsuarioAplicacaoBancoIT extends IntegrationTest {

    @Autowired
    private DataSource dataSource;

    private Connection comoAplicacao() throws SQLException {
        String url = ((HikariDataSource) dataSource).getJdbcUrl();
        return DriverManager.getConnection(url, "app_teste", "senha-do-app-de-teste");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "SELECT count(*) FROM usuarios",
                "UPDATE usuarios SET nome = nome WHERE id = -1",
                "DELETE FROM tokens_uso_unico WHERE id = -1",
                "INSERT INTO auditoria_login (email, resultado, ip, criado_em) VALUES ('x@teste.celke.uno', 'SUCESSO', '127.0.0.1', now())"
            })
    @DisplayName("lê e grava dados")
    void leEGravaDados(String sql) throws SQLException {
        try (Connection conexao = comoAplicacao();
                Statement comando = conexao.createStatement()) {
            conexao.setAutoCommit(false);
            comando.execute(sql);
            conexao.rollback();
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "CREATE TABLE invasora (id INT)",
                "DROP TABLE usuarios",
                "ALTER TABLE usuarios ADD COLUMN invasora INT",
                "TRUNCATE auditoria",
                "SELECT count(*) FROM flyway_schema_history"
            })
    @DisplayName("não muda a estrutura do banco nem lê o histórico das migrations")
    void semDdl(String sql) throws SQLException {
        try (Connection conexao = comoAplicacao();
                Statement comando = conexao.createStatement()) {
            assertThatThrownBy(() -> comando.execute(sql))
                    .isInstanceOf(SQLException.class)
                    .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("42501"));
        }
    }
}
