package br.com.celke.cursos.curso;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Cobre: posição de módulos e aulas — criação no fim, reordenação (restrição única adiável no
 * PostgreSQL), lista inválida, exclusão que refaz a sequência e criação simultânea sem posição
 * repetida (trava do pai).
 * Não cobre: regras puras de ordenação (OrdenacaoTest).
 * Dado criado: curso it-<stamp> com uma turma.
 */
class ConteudoOrdemIT extends IntegrationTest {

    private CursoITHelper adm;
    private long turma;

    @BeforeEach
    void criarTurma() {
        adm = new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA)));
        String slug = stamp();
        turma = adm.criarTurma(adm.criarCurso("IT Curso " + slug, slug), "Turma");
    }

    private String modulos() {
        return "/api/adm/turmas/" + turma + "/modulos";
    }

    @Test
    @DisplayName("módulos: entram no fim, são reordenados e a exclusão refaz a sequência")
    void ordemDosModulos() {
        long a = adm.criarModulo(turma, "A");
        long b = adm.criarModulo(turma, "B");
        long c = adm.criarModulo(turma, "C");
        assertThat(adm.enviar("GET", modulos(), null)).bodyJson().isStrictlyEqualTo("""
                        [{"id":%d,"turmaId":%d,"titulo":"A","ordem":1,"status":"ATIVO"},
                         {"id":%d,"turmaId":%d,"titulo":"B","ordem":2,"status":"ATIVO"},
                         {"id":%d,"turmaId":%d,"titulo":"C","ordem":3,"status":"ATIVO"}]
                        """.formatted(
                        a, turma, b, turma, c, turma));

        // Troca todas as posições numa transação: a restrição única só é conferida no commit
        assertThat(adm.enviar("PUT", modulos() + "/ordem", "{\"ids\":[%d,%d,%d]}".formatted(c, a, b)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].titulo")
                .asArray()
                .containsExactly("C", "A", "B");
        assertThat(adm.enviar("GET", modulos(), null))
                .bodyJson()
                .extractingPath("$[*].ordem")
                .asArray()
                .containsExactly(1, 2, 3);

        assertThat(adm.enviar("DELETE", "/api/adm/modulos/" + a, null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(adm.enviar("GET", modulos(), null))
                .bodyJson()
                .isLenientlyEqualTo("[{\"titulo\":\"C\",\"ordem\":1},{\"titulo\":\"B\",\"ordem\":2}]");
    }

    @Test
    @DisplayName("reordenar com lista incompleta, repetida ou com item de outra turma responde 400 sem mudar nada")
    void reordenacaoInvalida() {
        long a = adm.criarModulo(turma, "A");
        long b = adm.criarModulo(turma, "B");
        String slug = stamp();
        long deOutraTurma = adm.criarModulo(adm.criarTurma(adm.criarCurso("IT " + slug, slug), "T"), "X");

        for (String ids :
                List.of("[%d]".formatted(a), "[%d,%d]".formatted(a, a), "[%d,%d]".formatted(a, deOutraTurma))) {
            assertThat(adm.enviar("PUT", modulos() + "/ordem", "{\"ids\":" + ids + "}"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .extractingPath("$.detail")
                    .isEqualTo("A nova ordem deve ter todos os itens atuais, sem repetir nenhum.");
        }
        assertThat(adm.enviar("GET", modulos(), null))
                .bodyJson()
                .extractingPath("$[*].id")
                .asArray()
                .containsExactly((int) a, (int) b);
    }

    @Test
    @DisplayName("aulas: entram no fim com os dados normalizados, são reordenadas e a exclusão refaz a sequência")
    void ordemDasAulas() {
        long modulo = adm.criarModulo(turma, "Módulo");
        String aulas = "/api/adm/modulos/" + modulo + "/aulas";
        var criacao =
                adm.enviar("POST", aulas, "{\"titulo\":\" Boas-vindas \",\"descricao\":\"  \",\"gratuita\":true}");
        long a = CursoITHelper.idCriado(criacao);
        assertThat(criacao).bodyJson().isStrictlyEqualTo("""
                        {"id":%d,"moduloId":%d,"titulo":"Boas-vindas","descricao":null,"ordem":1,
                         "gratuita":true,"status":"ATIVO"}
                        """.formatted(a, modulo));
        long b = adm.criarAula(modulo, "B", false);
        long c = adm.criarAula(modulo, "C", false);

        assertThat(adm.enviar("PUT", aulas + "/ordem", "{\"ids\":[%d,%d,%d]}".formatted(b, c, a)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].ordem")
                .asArray()
                .containsExactly(1, 2, 3);
        assertThat(adm.enviar("DELETE", "/api/adm/aulas/" + c, null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(adm.enviar("GET", aulas, null))
                .bodyJson()
                .isLenientlyEqualTo("[{\"id\":%d,\"ordem\":1},{\"id\":%d,\"ordem\":2}]".formatted(b, a));
    }

    @Test
    @DisplayName("módulos criados ao mesmo tempo recebem posições distintas")
    void criacaoSimultanea() throws Exception {
        List<Callable<Long>> tarefas = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String titulo = "Simultâneo " + i;
            tarefas.add(() -> adm.criarModulo(turma, titulo));
        }
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var futuro : executor.invokeAll(tarefas)) {
                futuro.get();
            }
        }

        assertThat(adm.enviar("GET", modulos(), null))
                .bodyJson()
                .extractingPath("$[*].ordem")
                .asArray()
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
    }
}
