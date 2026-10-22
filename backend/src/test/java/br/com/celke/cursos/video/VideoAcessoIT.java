package br.com.celke.cursos.video;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
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
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: matriz de papéis das rotas de vídeo do ADM ({@code /api/adm/aulas/{id}/video}: 401 anônimo,
 * 403 ALUNO, ADMIN passa pela segurança).
 * Não cobre: o comportamento de cada rota (VideoEnvioIT).
 * Dado criado: a cada caso, um curso it-<stamp> com turma, módulo e aula sem vídeo (id real nas rotas).
 */
class VideoAcessoIT extends IntegrationTest {

    private String tokenAdmin;
    private String tokenAluno;
    private long aula;

    @BeforeEach
    void criarAula() {
        tokenAdmin = login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA);
        tokenAluno = login(UsuariosSeed.ALUNO_EMAIL, UsuariosSeed.SENHA);
        CursoITHelper adm = new CursoITHelper(mvc, bearer(tokenAdmin));
        String slug = stamp();
        long curso = adm.criarCurso("IT Curso " + slug, slug);
        long modulo = adm.criarModulo(adm.criarTurma(curso, "Turma"), "Módulo");
        aula = adm.criarAula(modulo, "Aula", false);
    }

    /**
     * Rotas com corpo válido e o status esperado para o ADMIN numa aula sem vídeo: o envio abre um
     * upload de verdade; as demais respondem que não há vídeo ou envio em andamento.
     */
    static Stream<Arguments> rotas() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/adm/aulas/{a}/video", null, HttpStatus.NOT_FOUND),
                Arguments.of(
                        HttpMethod.POST,
                        "/api/adm/aulas/{a}/video/envio",
                        "{\"nomeArquivo\":\"a.mp4\",\"contentType\":\"video/mp4\",\"tamanho\":10}",
                        HttpStatus.OK),
                Arguments.of(
                        HttpMethod.POST,
                        "/api/adm/aulas/{a}/video/envio/conclusao",
                        "{\"partes\":[{\"numero\":1,\"etag\":\"x\"}]}",
                        HttpStatus.CONFLICT),
                Arguments.of(HttpMethod.POST, "/api/adm/aulas/{a}/video/reprocessamento", null, HttpStatus.NOT_FOUND),
                Arguments.of(HttpMethod.DELETE, "/api/adm/aulas/{a}/video", null, HttpStatus.NOT_FOUND));
    }

    private MvcTestResult chamar(HttpMethod metodo, String rota, String corpo, String token) {
        var requisicao = mvc.method(metodo).uri(rota.replace("{a}", String.valueOf(aula)));
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
        assertThat(chamar(metodo, rota, corpo, null))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Autenticação necessária.");
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

    @Test
    @DisplayName("ALUNO recebe 403 mesmo com corpo inválido: o papel é conferido antes do corpo")
    void papelAntesDoCorpo() {
        assertThat(chamar(HttpMethod.POST, "/api/adm/aulas/{a}/video/envio", "{}", tokenAluno))
                .hasStatus(HttpStatus.FORBIDDEN);
    }
}
