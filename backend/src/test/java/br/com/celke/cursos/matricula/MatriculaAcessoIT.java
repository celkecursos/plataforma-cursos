package br.com.celke.cursos.matricula;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: matriz de papéis das rotas de matrícula — ADM ({@code /api/adm}: 401 anônimo, 403 ALUNO,
 * ADMIN passa) e área do aluno ({@code /api/aluno}: privada, 401 anônimo; ALUNO e ADMIN matriculados
 * acessam).
 * Não cobre: regras de cada rota (MatriculaAdmIT, AlunoCursosIT, AcessoConteudoIT).
 * Dado criado: a cada caso, curso it-<stamp> publicado, um ALUNO e um ADMIN próprios, matriculados.
 */
class MatriculaAcessoIT extends IntegrationTest {

    private String tokenAdmin;
    private String tokenAluno;
    private MatriculaITHelper.Estrutura estrutura;
    private long matriculaAluno;
    private long matriculaAdmin;
    private long matriculaOutra;

    @BeforeEach
    void criarEstrutura() {
        MatriculaITHelper helper = new MatriculaITHelper(
                new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA))));
        estrutura = helper.cursoPublicado(stamp());
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);
        UsuarioTeste admin = criarUsuario(Papel.ADMIN);
        Instant ontem = Instant.now().minusSeconds(86_400);
        matriculaAluno = helper.matricula(estrutura.turma(), aluno.email(), ontem, null);
        matriculaAdmin = helper.matricula(estrutura.turma(), admin.email(), ontem, null);
        matriculaOutra =
                helper.matricula(estrutura.turma(), criarUsuario(Papel.ALUNO).email(), ontem, null);
        tokenAluno = login(aluno.email(), aluno.senha());
        tokenAdmin = login(admin.email(), admin.senha());
    }

    static Stream<Arguments> rotasAdm() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/adm/turmas/{t}/matriculas", null, HttpStatus.OK),
                Arguments.of(
                        HttpMethod.POST,
                        "/api/adm/turmas/{t}/matriculas",
                        "{\"email\":\"{email}\",\"formaPagamento\":\"CORTESIA\"}",
                        HttpStatus.CREATED),
                Arguments.of(
                        HttpMethod.PUT,
                        "/api/adm/matriculas/{outra}/periodo",
                        "{\"inicio\":\"2026-01-01T00:00:00Z\"}",
                        HttpStatus.OK),
                Arguments.of(HttpMethod.POST, "/api/adm/matriculas/{outra}/cancelamento", null, HttpStatus.OK));
    }

    /** Rotas do aluno; {matricula} é a matrícula de quem chama. */
    static Stream<Arguments> rotasAluno() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/aluno/cursos", HttpStatus.OK),
                Arguments.of(HttpMethod.GET, "/api/aluno/turmas/{t}", HttpStatus.OK),
                Arguments.of(HttpMethod.GET, "/api/aluno/aulas/{paga}", HttpStatus.OK),
                Arguments.of(HttpMethod.PUT, "/api/aluno/matriculas/{matricula}/arquivamento", HttpStatus.NO_CONTENT),
                Arguments.of(
                        HttpMethod.DELETE, "/api/aluno/matriculas/{matricula}/arquivamento", HttpStatus.NO_CONTENT));
    }

    private MvcTestResult chamar(HttpMethod metodo, String rota, String corpo, String token, long matricula) {
        String uri = rota.replace("{t}", String.valueOf(estrutura.turma()))
                .replace("{paga}", String.valueOf(estrutura.aulaPaga()))
                .replace("{outra}", String.valueOf(matriculaOutra))
                .replace("{matricula}", String.valueOf(matricula));
        var requisicao = mvc.method(metodo).uri(uri);
        if (corpo != null) {
            String email = criarUsuario(Papel.ALUNO).email();
            requisicao = requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo.replace("{email}", email));
        }
        if (token != null) {
            requisicao = requisicao.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return requisicao.exchange();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rotasAdm")
    @DisplayName("ADM: anônimo 401, ALUNO 403")
    void admSemPapel(HttpMethod metodo, String rota, String corpo, HttpStatus admin) {
        assertThat(chamar(metodo, rota, corpo, null, 0)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(metodo, rota, corpo, tokenAluno, 0))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Acesso negado.");
    }

    @ParameterizedTest(name = "{0} {1} → {3}")
    @MethodSource("rotasAdm")
    @DisplayName("ADM: ADMIN passa")
    void admAdmin(HttpMethod metodo, String rota, String corpo, HttpStatus admin) {
        assertThat(chamar(metodo, rota, corpo, tokenAdmin, 0)).hasStatus(admin);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rotasAluno")
    @DisplayName("aluno: anônimo 401")
    void alunoAnonimo(HttpMethod metodo, String rota, HttpStatus esperado) {
        assertThat(chamar(metodo, rota, null, null, matriculaAluno))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Autenticação necessária.");
    }

    @ParameterizedTest(name = "{0} {1} → {2}")
    @MethodSource("rotasAluno")
    @DisplayName("aluno: ALUNO matriculado acessa")
    void alunoAluno(HttpMethod metodo, String rota, HttpStatus esperado) {
        assertThat(chamar(metodo, rota, null, tokenAluno, matriculaAluno)).hasStatus(esperado);
    }

    @ParameterizedTest(name = "{0} {1} → {2}")
    @MethodSource("rotasAluno")
    @DisplayName("aluno: ADMIN matriculado acessa")
    void alunoAdmin(HttpMethod metodo, String rota, HttpStatus esperado) {
        assertThat(chamar(metodo, rota, null, tokenAdmin, matriculaAdmin)).hasStatus(esperado);
    }

    @Test
    @DisplayName("matrícula de outro aluno responde 404 no arquivamento (sem revelar que existe)")
    void matriculaDeOutroAluno() {
        assertThat(chamar(
                        HttpMethod.PUT,
                        "/api/aluno/matriculas/{matricula}/arquivamento",
                        null,
                        tokenAluno,
                        matriculaOutra))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Matrícula não encontrada.");
    }
}
