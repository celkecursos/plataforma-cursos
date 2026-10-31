package br.com.celke.cursos.matricula;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: "meus cursos" — abas ativos, arquivados e expirados; matrícula futura em ativos; cancelada
 * fora de todas; arquivar e desarquivar; matrícula de outro aluno (404, nada muda).
 * Não cobre: acesso ao conteúdo (AcessoConteudoIT).
 * Dado criado: cursos it-<stamp> e alunos próprios.
 */
class AlunoCursosIT extends IntegrationTest {

    private static final Duration DIA = Duration.ofDays(1);

    private MatriculaITHelper helper;
    private UsuarioTeste aluno;
    private String token;

    @BeforeEach
    void criarAluno() {
        helper = new MatriculaITHelper(
                new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA))));
        aluno = criarUsuario(Papel.ALUNO);
        token = login(aluno.email(), aluno.senha());
    }

    private MvcTestResult chamar(String metodo, String uri, String tokenDe) {
        return mvc.method(org.springframework.http.HttpMethod.valueOf(metodo))
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDe))
                .exchange();
    }

    private MvcTestResult aba(String situacao) {
        return chamar("GET", "/api/aluno/cursos?situacao=" + situacao, token);
    }

    @Test
    @DisplayName("abas: vigente e futura em ativos, vencida em expirados, cancelada em nenhuma; arquivar move de aba")
    void abas() {
        Instant agora = Instant.now();
        String slugVigente = "b-" + stamp();
        String slugFutura = "a-" + stamp();
        String slugVencida = stamp();
        String slugCancelada = stamp();
        long vigente =
                helper.matricula(helper.cursoPublicado(slugVigente).turma(), aluno.email(), agora.minus(DIA), null);
        helper.matricula(helper.cursoPublicado(slugFutura).turma(), aluno.email(), agora.plus(DIA), null);
        helper.matricula(
                helper.cursoPublicado(slugVencida).turma(),
                aluno.email(),
                agora.minus(DIA.multipliedBy(3)),
                agora.minus(DIA));
        helper.cancelar(
                helper.matricula(helper.cursoPublicado(slugCancelada).turma(), aluno.email(), agora.minus(DIA), null));

        assertThat(chamar("GET", "/api/aluno/cursos", token))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"cursoSlug":"%s","situacao":"AGUARDANDO_INICIO","arquivada":false},
                         {"cursoSlug":"%s","situacao":"VIGENTE","arquivada":false,"turmaNome":"Turma","capaUrl":null}]
                        """.formatted(slugFutura, slugVigente));
        assertThat(aba("EXPIRADOS")).bodyJson().isLenientlyEqualTo("""
                        [{"cursoTitulo":"IT Curso %s","cursoSlug":"%s","turmaNome":"Turma","capaUrl":null,
                          "situacao":"VENCIDA","arquivada":false}]
                        """.formatted(slugVencida, slugVencida));
        assertThat(aba("EXPIRADOS"))
                .bodyJson()
                .extractingPath("$[*].cursoSlug")
                .asArray()
                .containsExactly(slugVencida);
        assertThat(aba("ARQUIVADOS")).bodyJson().extractingPath("$").asArray().isEmpty();

        assertThat(chamar("PUT", "/api/aluno/matriculas/" + vigente + "/arquivamento", token))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(aba("ARQUIVADOS"))
                .bodyJson()
                .isLenientlyEqualTo("[{\"matriculaId\":%d,\"arquivada\":true}]".formatted(vigente));
        assertThat(aba("ATIVOS"))
                .bodyJson()
                .extractingPath("$[*].cursoSlug")
                .asArray()
                .containsExactly(slugFutura);

        assertThat(chamar("DELETE", "/api/aluno/matriculas/" + vigente + "/arquivamento", token))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(aba("ATIVOS"))
                .bodyJson()
                .extractingPath("$[*].cursoSlug")
                .asArray()
                .hasSize(2);
    }

    @Test
    @DisplayName("outro aluno não arquiva a matrícula e não vê o curso na lista dele")
    void matriculaDeOutroAluno() {
        String slug = stamp();
        long matricula = helper.matricula(helper.cursoPublicado(slug).turma(), aluno.email(), null, null);
        UsuarioTeste outro = criarUsuario(Papel.ALUNO);
        String tokenOutro = login(outro.email(), outro.senha());

        assertThat(chamar("PUT", "/api/aluno/matriculas/" + matricula + "/arquivamento", tokenOutro))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Matrícula não encontrada.");
        assertThat(chamar("DELETE", "/api/aluno/matriculas/" + matricula + "/arquivamento", tokenOutro))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(chamar("GET", "/api/aluno/cursos", tokenOutro))
                .bodyJson()
                .extractingPath("$")
                .asArray()
                .isEmpty();
        assertThat(aba("ATIVOS"))
                .bodyJson()
                .isLenientlyEqualTo("[{\"matriculaId\":%d,\"arquivada\":false}]".formatted(matricula));
    }

    @Test
    @DisplayName("aba inválida responde 400")
    void abaInvalida() {
        assertThat(aba("TODOS")).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
