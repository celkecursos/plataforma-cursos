package br.com.celke.cursos.curso;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
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

/**
 * Cobre: matriz de papéis de todas as rotas de cursos — ADM ({@code /api/adm}: 401 anônimo, 403
 * ALUNO, ADMIN passa pela segurança) e catálogo público (mesma resposta para os três).
 * Não cobre: o comportamento de cada rota (CursoCrudIT, ConteudoOrdemIT, CapaCursoIT, CatalogoIT).
 * Dado criado: a cada caso, um curso it-<stamp> com turma, módulo e aula (ids reais nas rotas).
 */
class CursoAcessoIT extends IntegrationTest {

    private String tokenAdmin;
    private String tokenAluno;
    private long curso;
    private long turma;
    private long modulo;
    private long aula;
    private String slug;

    @BeforeEach
    void criarEstrutura() {
        tokenAdmin = login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA);
        tokenAluno = login(UsuariosSeed.ALUNO_EMAIL, UsuariosSeed.SENHA);
        CursoITHelper adm = new CursoITHelper(mvc, bearer(tokenAdmin));
        slug = stamp();
        curso = adm.criarCurso("IT Curso " + slug, slug);
        turma = adm.criarTurma(curso, "Turma");
        modulo = adm.criarModulo(turma, "Módulo");
        aula = adm.criarAula(modulo, "Aula", false);
    }

    /**
     * Rotas do ADM com corpo válido e o status esperado para o ADMIN. As exclusões de curso, turma e
     * módulo dão 409 porque cada um tem filhos: passam pela segurança sem apagar a estrutura.
     */
    static Stream<Arguments> rotasAdm() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/adm/cursos", null, HttpStatus.OK),
                Arguments.of(
                        HttpMethod.POST,
                        "/api/adm/cursos",
                        "{\"titulo\":\"IT {stamp}\",\"resumo\":\"R\"}",
                        HttpStatus.CREATED),
                Arguments.of(HttpMethod.GET, "/api/adm/cursos/{c}", null, HttpStatus.OK),
                Arguments.of(
                        HttpMethod.PUT,
                        "/api/adm/cursos/{c}",
                        "{\"titulo\":\"IT\",\"slug\":\"{slug}\",\"resumo\":\"R\"}",
                        HttpStatus.OK),
                Arguments.of(
                        HttpMethod.PATCH, "/api/adm/cursos/{c}/status", "{\"status\":\"RASCUNHO\"}", HttpStatus.OK),
                Arguments.of(HttpMethod.DELETE, "/api/adm/cursos/{c}", null, HttpStatus.CONFLICT),
                Arguments.of(
                        HttpMethod.POST,
                        "/api/adm/cursos/{c}/capa/envio",
                        "{\"contentType\":\"image/png\",\"tamanho\":10}",
                        HttpStatus.OK),
                Arguments.of(HttpMethod.PUT, "/api/adm/cursos/{c}/capa", "{\"chave\":\"x\"}", HttpStatus.BAD_REQUEST),
                Arguments.of(HttpMethod.DELETE, "/api/adm/cursos/{c}/capa", null, HttpStatus.NO_CONTENT),
                Arguments.of(HttpMethod.GET, "/api/adm/cursos/{c}/turmas", null, HttpStatus.OK),
                Arguments.of(HttpMethod.POST, "/api/adm/cursos/{c}/turmas", "{\"nome\":\"T\"}", HttpStatus.CREATED),
                Arguments.of(HttpMethod.PUT, "/api/adm/turmas/{t}", "{\"nome\":\"T\"}", HttpStatus.OK),
                Arguments.of(HttpMethod.PATCH, "/api/adm/turmas/{t}/status", "{\"status\":\"ATIVA\"}", HttpStatus.OK),
                Arguments.of(HttpMethod.DELETE, "/api/adm/turmas/{t}", null, HttpStatus.CONFLICT),
                Arguments.of(HttpMethod.GET, "/api/adm/turmas/{t}/modulos", null, HttpStatus.OK),
                Arguments.of(HttpMethod.POST, "/api/adm/turmas/{t}/modulos", "{\"titulo\":\"M\"}", HttpStatus.CREATED),
                Arguments.of(HttpMethod.PUT, "/api/adm/turmas/{t}/modulos/ordem", "{\"ids\":[{m}]}", HttpStatus.OK),
                Arguments.of(HttpMethod.PUT, "/api/adm/modulos/{m}", "{\"titulo\":\"M\"}", HttpStatus.OK),
                Arguments.of(
                        HttpMethod.PATCH, "/api/adm/modulos/{m}/status", "{\"status\":\"INATIVO\"}", HttpStatus.OK),
                Arguments.of(HttpMethod.DELETE, "/api/adm/modulos/{m}", null, HttpStatus.CONFLICT),
                Arguments.of(HttpMethod.GET, "/api/adm/modulos/{m}/aulas", null, HttpStatus.OK),
                Arguments.of(HttpMethod.POST, "/api/adm/modulos/{m}/aulas", "{\"titulo\":\"A\"}", HttpStatus.CREATED),
                Arguments.of(HttpMethod.PUT, "/api/adm/modulos/{m}/aulas/ordem", "{\"ids\":[{a}]}", HttpStatus.OK),
                Arguments.of(HttpMethod.PUT, "/api/adm/aulas/{a}", "{\"titulo\":\"A\"}", HttpStatus.OK),
                Arguments.of(HttpMethod.PATCH, "/api/adm/aulas/{a}/status", "{\"status\":\"INATIVO\"}", HttpStatus.OK),
                Arguments.of(HttpMethod.DELETE, "/api/adm/aulas/{a}", null, HttpStatus.NO_CONTENT));
    }

    private String preencher(String modelo) {
        return modelo == null
                ? null
                : modelo.replace("{c}", String.valueOf(curso))
                        .replace("{t}", String.valueOf(turma))
                        .replace("{m}", String.valueOf(modulo))
                        .replace("{a}", String.valueOf(aula))
                        .replace("{slug}", slug)
                        .replace("{stamp}", stamp());
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult chamar(
            HttpMethod metodo, String rota, String corpo, String token) {
        var requisicao = mvc.method(metodo).uri(preencher(rota));
        if (corpo != null) {
            requisicao = requisicao.contentType(MediaType.APPLICATION_JSON).content(preencher(corpo));
        }
        if (token != null) {
            requisicao = requisicao.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return requisicao.exchange();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rotasAdm")
    @DisplayName("ADM: anônimo recebe 401")
    void admAnonimo(HttpMethod metodo, String rota, String corpo, HttpStatus admin) {
        assertThat(chamar(metodo, rota, corpo, null))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Autenticação necessária.");
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rotasAdm")
    @DisplayName("ADM: ALUNO recebe 403")
    void admAluno(HttpMethod metodo, String rota, String corpo, HttpStatus admin) {
        assertThat(chamar(metodo, rota, corpo, tokenAluno))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Acesso negado.");
    }

    @ParameterizedTest(name = "{0} {1} → {3}")
    @MethodSource("rotasAdm")
    @DisplayName("ADM: ADMIN passa pela segurança")
    void admAdmin(HttpMethod metodo, String rota, String corpo, HttpStatus admin) {
        assertThat(chamar(metodo, rota, corpo, tokenAdmin)).hasStatus(admin);
    }

    @Test
    @DisplayName("ALUNO recebe 403 mesmo com corpo inválido: o papel é conferido antes do corpo")
    void papelAntesDoCorpo() {
        assertThat(chamar(HttpMethod.POST, "/api/adm/cursos", "{}", tokenAluno)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("papeis")
    @DisplayName("catálogo: mesma resposta para anônimo, ALUNO e ADMIN")
    void catalogoPublico(String papel) {
        new CursoITHelper(mvc, bearer(tokenAdmin)).publicar(curso);
        String token = switch (papel) {
            case "ALUNO" -> tokenAluno;
            case "ADMIN" -> tokenAdmin;
            default -> null;
        };

        assertThat(chamar(HttpMethod.GET, "/api/cursos", null, token)).hasStatusOk();
        assertThat(chamar(HttpMethod.GET, "/api/cursos/{slug}", null, token))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.slug")
                .isEqualTo(slug);
        assertThat(chamar(HttpMethod.GET, "/api/cursos/{slug}/capa", null, token))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Curso sem capa.");
    }

    static Stream<String> papeis() {
        return Stream.of("anônimo", "ALUNO", "ADMIN");
    }

    @Test
    @DisplayName("catálogo: métodos não declarados exigem token")
    void metodoNaoDeclarado() {
        assertThat(chamar(HttpMethod.POST, "/api/cursos", "{}", null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.DELETE, "/api/cursos/{slug}", null, null)).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
