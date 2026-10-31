package br.com.celke.cursos.forum;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.matricula.MatriculaITHelper;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: matriz de papéis das rotas de moderação do fórum ({@code /api/adm}: 401 anônimo, 403 ALUNO —
 * mesmo o autor do tópico —, ADMIN passa pela segurança).
 * Não cobre: o comportamento das rotas (ForumIT).
 * Dado criado: a cada caso, curso it-<stamp> publicado e um aluno próprio matriculado, com um tópico e
 * uma resposta (ids reais nas rotas).
 */
class ForumAcessoIT extends IntegrationTest {

    private String tokenAdmin;
    private String tokenAluno;
    private long turma;
    private long topico;
    private Object resposta;

    @BeforeEach
    void criarTopico() {
        tokenAdmin = login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA);
        MatriculaITHelper helper = new MatriculaITHelper(new CursoITHelper(mvc, bearer(tokenAdmin)));
        var estrutura = helper.cursoPublicado(stamp());
        turma = estrutura.turma();
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);
        helper.matricula(turma, aluno.email(), Instant.now().minus(Duration.ofDays(1)), null);
        tokenAluno = login(aluno.email(), aluno.senha());
        topico = CursoITHelper.idCriado(chamar(
                HttpMethod.POST,
                "/api/aluno/turmas/" + turma + "/forum",
                "{\"titulo\":\"T\",\"corpo\":\"C\"}",
                tokenAluno));
        resposta = JsonPath.read(
                CursoITHelper.corpo(chamar(
                        HttpMethod.POST,
                        "/api/aluno/forum/topicos/" + topico + "/respostas",
                        "{\"corpo\":\"R\"}",
                        tokenAluno)),
                "$.respostas[0].id");
    }

    static Stream<Arguments> rotas() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/adm/turmas/{t}/forum", null),
                Arguments.of(HttpMethod.GET, "/api/adm/forum/topicos/{p}", null),
                Arguments.of(HttpMethod.PATCH, "/api/adm/forum/topicos/{p}", "{\"fixado\":true}"),
                Arguments.of(HttpMethod.PATCH, "/api/adm/forum/respostas/{r}", "{\"oculto\":false}"),
                Arguments.of(HttpMethod.POST, "/api/adm/forum/topicos/{p}/respostas", "{\"corpo\":\"Oficial\"}"));
    }

    private MvcTestResult chamar(HttpMethod metodo, String rota, String corpo, String token) {
        String uri = rota.replace("{t}", String.valueOf(turma))
                .replace("{p}", String.valueOf(topico))
                .replace("{r}", String.valueOf(resposta));
        var requisicao = mvc.method(metodo).uri(uri);
        if (corpo != null) {
            requisicao = requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        if (token != null) {
            requisicao = requisicao.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return requisicao.exchange();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rotas")
    @DisplayName("anônimo recebe 401")
    void anonimo(HttpMethod metodo, String rota, String corpo) {
        assertThat(chamar(metodo, rota, corpo, null)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rotas")
    @DisplayName("ALUNO (autor do tópico) recebe 403")
    void aluno(HttpMethod metodo, String rota, String corpo) {
        assertThat(chamar(metodo, rota, corpo, tokenAluno))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Acesso negado.");
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rotas")
    @DisplayName("ADMIN passa pela segurança")
    void admin(HttpMethod metodo, String rota, String corpo) {
        assertThat(chamar(metodo, rota, corpo, tokenAdmin)).hasStatusOk();
    }
}
