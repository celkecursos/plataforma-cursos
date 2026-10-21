package br.com.celke.cursos.usuario.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UsuarioTest {

    private static final Instant AGORA = Instant.parse("2026-01-10T12:00:00Z");

    @ParameterizedTest(name = "[{0}]")
    @CsvSource({
        "ana@celke.uno,          ana@celke.uno",
        "Ana@Celke.UNO,          ana@celke.uno",
        "'  ana@celke.uno  ',    ana@celke.uno",
    })
    @DisplayName("normaliza o e-mail para minúsculas e sem espaços nas pontas")
    void normalizaEmail(String recebido, String esperado) {
        assertThat(Usuario.normalizarEmail(recebido)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("novo usuário nasce ativo, com nome sem espaços nas pontas e e-mail normalizado")
    void novoUsuario() {
        Usuario usuario = new Usuario("  Ana  ", " Ana@Celke.uno ", "{argon2}hash", Set.of(Papel.ALUNO), AGORA);

        assertThat(usuario.getNome()).isEqualTo("Ana");
        assertThat(usuario.getEmail()).isEqualTo("ana@celke.uno");
        assertThat(usuario.isAtivo()).isTrue();
        assertThat(usuario.getPapeis()).containsExactly(Papel.ALUNO);
    }
}
