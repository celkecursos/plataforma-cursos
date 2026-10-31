package br.com.celke.cursos.matricula;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * Cobre: matrícula manual pelo ADM — matricular (padrões, Location, dados do aluno na lista),
 * matrícula repetida, nova matrícula depois de cancelar, turma encerrada, usuário inexistente,
 * transação repetida, período inválido, alteração de matrícula cancelada, validação e turma com
 * matrículas que não pode ser excluída.
 * Não cobre: acesso do aluno ao conteúdo (AcessoConteudoIT).
 * Dado criado: cursos it-<stamp> e alunos it-<stamp>@teste.celke.uno.
 */
class MatriculaAdmIT extends IntegrationTest {

    private MatriculaITHelper helper;
    private CursoITHelper adm;
    private long turma;

    @BeforeEach
    void criarTurma() {
        adm = new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA)));
        helper = new MatriculaITHelper(adm);
        turma = helper.cursoPublicado(stamp()).turma();
    }

    @Test
    @DisplayName("matricula sem datas (começa agora, sem prazo), lista com o aluno, altera o período e cancela")
    void cicloDaMatricula() {
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);
        Instant antes = Instant.now();

        var criacao = adm.enviar(
                "POST",
                "/api/adm/turmas/" + turma + "/matriculas",
                "{\"email\":\"%s\",\"formaPagamento\":\"PIX\",\"transacao\":\" %s \"}"
                        .formatted(aluno.email().toUpperCase(), aluno.email()));
        long id = CursoITHelper.idCriado(criacao);
        assertThat(criacao.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/adm/matriculas/" + id);
        assertThat(criacao).bodyJson().isLenientlyEqualTo("""
                        {"turmaId":%d,"usuarioId":%d,"email":"%s","fim":null,"status":"ATIVA",
                         "situacao":"VIGENTE","formaPagamento":"PIX","transacao":"%s"}
                        """.formatted(
                        turma, aluno.id(), aluno.email(), aluno.email()));
        String inicio = com.jayway.jsonpath.JsonPath.read(CursoITHelper.corpo(criacao), "$.inicio");
        assertThat(Instant.parse(inicio)).isAfterOrEqualTo(antes.minusSeconds(1));

        assertThat(adm.enviar("GET", "/api/adm/turmas/" + turma + "/matriculas", null))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo(
                        "{\"itens\":[{\"id\":%d,\"nome\":\"Usuário %s\"}],\"total\":1}".formatted(id, aluno.email()));

        assertThat(adm.enviar(
                        "PUT",
                        "/api/adm/matriculas/" + id + "/periodo",
                        "{\"inicio\":\"2026-01-01T00:00:00Z\",\"fim\":\"2026-02-01T00:00:00Z\"}"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo(
                        "{\"inicio\":\"2026-01-01T00:00:00Z\",\"fim\":\"2026-02-01T00:00:00Z\",\"situacao\":\"VENCIDA\"}");

        assertThat(adm.enviar("POST", "/api/adm/matriculas/" + id + "/cancelamento", null))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"status\":\"CANCELADA\",\"situacao\":\"CANCELADA\"}");
        assertThat(adm.enviar("POST", "/api/adm/matriculas/" + id + "/cancelamento", null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A matrícula está cancelada.");
        assertThat(adm.enviar("PUT", "/api/adm/matriculas/" + id + "/periodo", "{\"inicio\":\"2026-01-01T00:00:00Z\"}"))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("matrícula repetida responde 409; depois de cancelar, o aluno pode ser matriculado de novo")
    void matriculaRepetida() {
        String email = criarUsuario(Papel.ALUNO).email();
        long primeira = helper.matricula(turma, email, null, null);

        assertThat(helper.matricular(turma, email, null, null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O aluno já tem matrícula nesta turma. Altere o período da matrícula atual.");

        helper.cancelar(primeira);
        assertThat(helper.matricular(turma, email, null, null)).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("turma encerrada, usuário inexistente e transação repetida são recusados")
    void recusas() {
        String email = criarUsuario(Papel.ALUNO).email();
        assertThat(helper.matricular(turma, "ninguem-" + stamp() + "@teste.celke.uno", null, null))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Usuário não encontrado.");
        assertThat(helper.matricular(999_999_999L, email, null, null))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Turma não encontrada.");

        String transacao = "{\"email\":\"%s\",\"formaPagamento\":\"CARTAO\",\"transacao\":\"tx-" + turma + "\"}";
        assertThat(adm.enviar("POST", "/api/adm/turmas/" + turma + "/matriculas", transacao.formatted(email)))
                .hasStatus(HttpStatus.CREATED);
        assertThat(adm.enviar(
                        "POST",
                        "/api/adm/turmas/" + turma + "/matriculas",
                        transacao.formatted(criarUsuario(Papel.ALUNO).email())))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Esta transação já gerou uma matrícula.");

        adm.status("/api/adm/turmas/" + turma + "/status", "ENCERRADA");
        assertThat(helper.matricular(turma, criarUsuario(Papel.ALUNO).email(), null, null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A turma está encerrada e não recebe novas matrículas.");
    }

    @Test
    @DisplayName("período inválido e corpo inválido respondem 400")
    void validacao() {
        String email = criarUsuario(Papel.ALUNO).email();
        assertThat(helper.matricular(
                        turma, email, Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-03-01T00:00:00Z")))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O fim da matrícula deve ser depois do início.");
        assertThat(adm.enviar("POST", "/api/adm/turmas/" + turma + "/matriculas", "{\"email\":\"x\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .asArray()
                .containsExactlyInAnyOrder("email", "formaPagamento");
    }

    @Test
    @DisplayName("turma com matrículas não é excluída, mesmo sem módulos")
    void turmaComMatriculas() {
        String slug = stamp();
        long semModulos = adm.criarTurma(adm.criarCurso("IT Curso " + slug, slug), "Sem módulos");
        helper.matricula(semModulos, criarUsuario(Papel.ALUNO).email(), null, null);

        assertThat(adm.enviar("DELETE", "/api/adm/turmas/" + semModulos, null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A turma tem matrículas. Encerre a turma.");
    }
}
