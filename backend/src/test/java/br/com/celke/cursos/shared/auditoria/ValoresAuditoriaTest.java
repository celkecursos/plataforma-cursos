package br.com.celke.cursos.shared.auditoria;

import static br.com.celke.cursos.support.fixtures.CursoFixture.curso;
import static br.com.celke.cursos.support.fixtures.CursoFixture.turma;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.pagamento.domain.Gateway;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValoresAuditoriaTest {

    @Test
    @DisplayName("valores simples: enum pelo nome, data e UUID em texto, binário marcado, entidade pelo id")
    void simples() {
        assertThat(ValoresAuditoria.simples("texto")).isEqualTo("texto");
        assertThat(ValoresAuditoria.simples(42L)).isEqualTo(42L);
        assertThat(ValoresAuditoria.simples(true)).isEqualTo(true);
        assertThat(ValoresAuditoria.simples(StatusTurma.ATIVA)).isEqualTo("ATIVA");
        assertThat(ValoresAuditoria.simples(Instant.parse("2026-10-22T13:00:00Z")))
                .isEqualTo("2026-10-22T13:00:00Z");
        UUID uuid = UUID.randomUUID();
        assertThat(ValoresAuditoria.simples(uuid)).isEqualTo(uuid.toString());
        assertThat(ValoresAuditoria.simples(new byte[] {1, 2})).isEqualTo("[binário]");
        assertThat(ValoresAuditoria.simples(List.of(Papel.ALUNO))).isEqualTo(List.of("ALUNO"));
        assertThat(ValoresAuditoria.simples(curso(7, "java"))).isEqualTo(7L);
        assertThat(ValoresAuditoria.simples(new StringBuilder("x"))).isEqualTo("x");
        assertThat(ValoresAuditoria.simples(null)).isNull();
    }

    @Test
    @DisplayName("campos sensíveis viram *** (mesmo nas superclasses), nulos ficam nulos")
    void sensiveis() {
        assertThat(ValoresAuditoria.sensiveis(Usuario.class)).containsExactly("senhaHash");
        assertThat(ValoresAuditoria.sensiveis(Gateway.class)).containsExactlyInAnyOrder("segredos", "webhookToken");
        assertThat(ValoresAuditoria.valor(Usuario.class, "senhaHash", "{argon2}abc"))
                .isEqualTo("***");
        assertThat(ValoresAuditoria.valor(Usuario.class, "senhaHash", null)).isNull();
        assertThat(ValoresAuditoria.valor(Usuario.class, "nome", "Ana")).isEqualTo("Ana");
    }

    @Test
    @DisplayName("campos escolhidos pelo índice (alteração) ou todos (inclusão e exclusão); sem estado, nulo")
    void campos() {
        String[] nomes = {"nome", "senhaHash", "papeis"};
        Object[] estado = {"Ana", "{argon2}abc", Set.of(Papel.ADMIN)};

        Map<String, Object> todos = new LinkedHashMap<>();
        todos.put("nome", "Ana");
        todos.put("senhaHash", "***");
        todos.put("papeis", List.of("ADMIN"));
        assertThat(ValoresAuditoria.campos(Usuario.class, nomes, estado, null)).containsExactlyEntriesOf(todos);
        assertThat(ValoresAuditoria.campos(Usuario.class, nomes, estado, new int[] {1}))
                .containsExactly(Map.entry("senhaHash", "***"));
        assertThat(ValoresAuditoria.campos(Usuario.class, nomes, null, null)).isNull();
    }

    @Test
    @DisplayName("entidades de log e de sessão ficam fora da trilha")
    void foraDaTrilha() {
        assertThat(OuvinteAuditoria.auditada(br.com.celke.cursos.progresso.domain.ProgressoAula.class))
                .isFalse();
        assertThat(OuvinteAuditoria.auditada(br.com.celke.cursos.auth.domain.RefreshToken.class))
                .isFalse();
        assertThat(OuvinteAuditoria.auditada(turma(1, curso(1, "x")).getClass()))
                .isTrue();
    }
}
