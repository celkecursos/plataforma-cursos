package br.com.celke.cursos.matricula;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: matriz de acesso ao conteúdo da AcessoAulaPolicy nas rotas da turma e da aula — matrícula
 * vigente, sem matrícula, vencida, cancelada, ainda não iniciada; aula gratuita sem matrícula (e em
 * curso não publicado); aula e módulo inativos; só conteúdo ativo na turma.
 * Não cobre: as combinações de datas caso a caso (AcessoAulaPolicyTest, MatriculaTest).
 * Dado criado: a cada caso, curso it-<stamp> publicado e um aluno próprio.
 */
class AcessoConteudoIT extends IntegrationTest {

    private static final Duration DIA = Duration.ofDays(1);

    private MatriculaITHelper helper;
    private MatriculaITHelper.Estrutura estrutura;
    private UsuarioTeste aluno;
    private String token;

    @BeforeEach
    void criarCursoEAluno() {
        helper = new MatriculaITHelper(
                new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA))));
        estrutura = helper.cursoPublicado(stamp());
        aluno = criarUsuario(Papel.ALUNO);
        token = login(aluno.email(), aluno.senha());
    }

    private MvcTestResult get(String uri) {
        return mvc.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange();
    }

    private MvcTestResult turma() {
        return get("/api/aluno/turmas/" + estrutura.turma());
    }

    private MvcTestResult aula(long aula) {
        return get("/api/aluno/aulas/" + aula);
    }

    /** Situação da matrícula do aluno: nenhuma, vencida, cancelada ou ainda não iniciada. */
    static Stream<Arguments> semMatriculaVigente() {
        return Stream.of(
                Arguments.of("sem matrícula"),
                Arguments.of("vencida"),
                Arguments.of("cancelada"),
                Arguments.of("ainda não iniciada"));
    }

    private void prepararMatricula(String situacao) {
        Instant agora = Instant.now();
        switch (situacao) {
            case "vencida" ->
                helper.matricula(estrutura.turma(), aluno.email(), agora.minus(DIA.multipliedBy(2)), agora.minus(DIA));
            case "cancelada" ->
                helper.cancelar(helper.matricula(estrutura.turma(), aluno.email(), agora.minus(DIA), null));
            case "ainda não iniciada" -> helper.matricula(estrutura.turma(), aluno.email(), agora.plus(DIA), null);
            default -> {}
        }
    }

    @Test
    @DisplayName("matrícula vigente: turma com o conteúdo e aula paga liberadas")
    void matriculaVigente() {
        Instant agora = Instant.now();
        helper.matricula(estrutura.turma(), aluno.email(), agora.minus(DIA), agora.plus(DIA));

        assertThat(turma()).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        {"turmaId":%d,"turmaNome":"Turma","modulos":[{"id":%d,"titulo":"Módulo","aulas":[
                          {"id":%d,"titulo":"Aula paga","gratuita":false},
                          {"id":%d,"titulo":"Aula gratuita","gratuita":true}]}]}
                        """.formatted(
                        estrutura.turma(), estrutura.modulo(), estrutura.aulaPaga(), estrutura.aulaGratuita()));
        assertThat(aula(estrutura.aulaPaga()))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"id\":%d,\"turmaId\":%d,\"titulo\":\"Aula paga\",\"gratuita\":false}"
                        .formatted(estrutura.aulaPaga(), estrutura.turma()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("semMatriculaVigente")
    @DisplayName("sem matrícula vigente: turma e aula paga 403; aula gratuita liberada")
    void semAcesso(String situacao) {
        prepararMatricula(situacao);

        assertThat(turma())
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Você não tem acesso a esta turma. Verifique o período da sua matrícula.");
        assertThat(aula(estrutura.aulaPaga()))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Você não tem acesso a esta aula. Verifique o período da sua matrícula.");
        assertThat(aula(estrutura.aulaGratuita())).hasStatusOk();
    }

    @Test
    @DisplayName("aula gratuita de curso que voltou para rascunho não abre sem matrícula")
    void gratuitaDeCursoNaoPublicado() {
        helper.adm().status("/api/adm/cursos/" + estrutura.curso() + "/status", "RASCUNHO");

        assertThat(aula(estrutura.aulaGratuita())).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("aula inativa ou de módulo inativo responde 404 mesmo com matrícula vigente; a turma esconde as duas")
    void conteudoInativo() {
        helper.matricula(estrutura.turma(), aluno.email(), Instant.now().minus(DIA), null);
        long outroModulo = helper.adm().criarModulo(estrutura.turma(), "Inativo");
        long aulaDoModuloInativo = helper.adm().criarAula(outroModulo, "Escondida", false);
        helper.adm().status("/api/adm/modulos/" + outroModulo + "/status", "INATIVO");
        helper.adm().status("/api/adm/aulas/" + estrutura.aulaPaga() + "/status", "INATIVO");

        assertThat(aula(estrutura.aulaPaga()))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Aula não encontrada.");
        assertThat(aula(aulaDoModuloInativo)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(turma())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.modulos[*].aulas[*].id")
                .asArray()
                .containsExactly((int) estrutura.aulaGratuita());
    }

    @Test
    @DisplayName("matrícula em outra turma não dá acesso a esta")
    void matriculaEmOutraTurma() {
        var outra = helper.cursoPublicado(stamp());
        helper.matricula(outra.turma(), aluno.email(), Instant.now().minus(DIA), null);

        assertThat(turma()).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(aula(estrutura.aulaPaga())).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(aula(outra.aulaPaga())).hasStatusOk();
    }
}
