package br.com.celke.cursos.arquivo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import java.nio.charset.StandardCharsets;
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
 * Cobre: matriz de papéis das rotas de arquivos de apoio do ADM (401 anônimo, 403 ALUNO, ADMIN passa
 * pela segurança).
 * Não cobre: o comportamento de cada rota e o acesso do aluno (ArquivoIT).
 * Dado criado: a cada caso, curso it-<stamp> com turma, módulo, aula e um arquivo TXT publicado na aula
 * (ids reais nas rotas).
 */
class ArquivoAcessoIT extends IntegrationTest {

    private String tokenAdmin;
    private String tokenAluno;
    private long modulo;
    private long aula;
    private long arquivo;

    @BeforeEach
    void criarEstrutura() throws Exception {
        tokenAdmin = login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA);
        tokenAluno = login(UsuariosSeed.ALUNO_EMAIL, UsuariosSeed.SENHA);
        CursoITHelper adm = new CursoITHelper(mvc, bearer(tokenAdmin));
        String slug = stamp();
        long curso = adm.criarCurso("IT Curso " + slug, slug);
        modulo = adm.criarModulo(adm.criarTurma(curso, "Turma"), "Módulo");
        aula = adm.criarAula(modulo, "Aula", false);
        arquivo = new ArquivoITHelper(adm)
                .publicar(
                        "/api/adm/aulas/" + aula,
                        "anotações da aula".getBytes(StandardCharsets.UTF_8),
                        "text/plain",
                        "Notas",
                        "notas.txt");
    }

    static Stream<Arguments> rotas() {
        String envio = "{\"contentType\":\"application/zip\",\"tamanho\":10}";
        String confirmacao = "{\"chave\":\"x\",\"titulo\":\"T\",\"nomeArquivo\":\"a.zip\"}";
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/adm/aulas/{a}/arquivos", null, HttpStatus.OK),
                Arguments.of(HttpMethod.GET, "/api/adm/modulos/{m}/arquivos", null, HttpStatus.OK),
                Arguments.of(HttpMethod.POST, "/api/adm/aulas/{a}/arquivos/envio", envio, HttpStatus.OK),
                Arguments.of(HttpMethod.POST, "/api/adm/modulos/{m}/arquivos/envio", envio, HttpStatus.OK),
                Arguments.of(HttpMethod.POST, "/api/adm/aulas/{a}/arquivos", confirmacao, HttpStatus.BAD_REQUEST),
                Arguments.of(HttpMethod.POST, "/api/adm/modulos/{m}/arquivos", confirmacao, HttpStatus.BAD_REQUEST),
                Arguments.of(HttpMethod.DELETE, "/api/adm/arquivos/{f}", null, HttpStatus.NO_CONTENT));
    }

    private MvcTestResult chamar(HttpMethod metodo, String rota, String corpo, String token) {
        String uri = rota.replace("{a}", String.valueOf(aula))
                .replace("{m}", String.valueOf(modulo))
                .replace("{f}", String.valueOf(arquivo));
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
    void anonimo(HttpMethod metodo, String rota, String corpo, HttpStatus admin) {
        assertThat(chamar(metodo, rota, corpo, null)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rotas")
    @DisplayName("ALUNO recebe 403")
    void aluno(HttpMethod metodo, String rota, String corpo, HttpStatus admin) {
        assertThat(chamar(metodo, rota, corpo, tokenAluno))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Acesso negado.");
    }

    @ParameterizedTest(name = "{0} {1} → {3}")
    @MethodSource("rotas")
    @DisplayName("ADMIN passa pela segurança")
    void admin(HttpMethod metodo, String rota, String corpo, HttpStatus admin) {
        assertThat(chamar(metodo, rota, corpo, tokenAdmin)).hasStatus(admin);
    }
}
