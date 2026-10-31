package br.com.celke.cursos.progresso;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.matricula.MatriculaITHelper;
import br.com.celke.cursos.progresso.infra.ProgressoAulaRepository;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: progresso do aluno — gravação parcial (posição, vista, revisar), percentual da turma (nenhuma,
 * parte e todas as aulas; turma sem aulas), matriz de acesso ao conteúdo (vigente, sem matrícula,
 * vencida, cancelada, ainda não iniciada, aula gratuita), anônimo, aula inativa, validação e o
 * isolamento entre alunos (o progresso de um nunca aparece para outro).
 * Não cobre: arredondamento do percentual e regras da entidade (PercentualConclusaoTest,
 * ProgressoAulaTest, ProgressoServiceTest).
 * Dado criado: curso it-<stamp> publicado com uma aula paga e uma gratuita, e alunos próprios.
 */
class ProgressoIT extends IntegrationTest {

    private static final Duration DIA = Duration.ofDays(1);

    @Autowired
    private ProgressoAulaRepository progressos;

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

    private MvcTestResult chamar(HttpMethod metodo, String uri, String corpo, String tokenUsado) {
        var requisicao = mvc.method(metodo).uri(uri);
        if (corpo != null) {
            requisicao = requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        if (tokenUsado != null) {
            requisicao = requisicao.header(HttpHeaders.AUTHORIZATION, bearer(tokenUsado));
        }
        return requisicao.exchange();
    }

    private MvcTestResult progresso(long aula) {
        return chamar(HttpMethod.GET, "/api/aluno/aulas/" + aula + "/progresso", null, token);
    }

    private MvcTestResult atualizar(long aula, String corpo) {
        return chamar(HttpMethod.PATCH, "/api/aluno/aulas/" + aula + "/progresso", corpo, token);
    }

    private MvcTestResult turma(long turma, String tokenUsado) {
        return chamar(HttpMethod.GET, "/api/aluno/turmas/" + turma + "/progresso", null, tokenUsado);
    }

    private void matricularVigente(String email) {
        Instant agora = Instant.now();
        helper.matricula(estrutura.turma(), email, agora.minus(DIA), agora.plus(DIA));
    }

    @Test
    @DisplayName("grava posição, vista e revisar aos poucos e reflete no percentual da turma")
    void cicloDoProgresso() {
        matricularVigente(aluno.email());
        long paga = estrutura.aulaPaga();

        assertThat(progresso(paga)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"aulaId":%d,"posicaoSegundos":0,"vista":false,"vistaEm":null,"revisar":false}
                """.formatted(paga));
        assertThat(turma(estrutura.turma(), token)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"turmaId":%d,"totalAulas":2,"aulasVistas":0,"percentual":0}
                """.formatted(
                        estrutura.turma()));

        // O player manda a posição; depois a aula é marcada como vista, sem perder a posição
        assertThat(atualizar(paga, "{\"posicaoSegundos\":95}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.posicaoSegundos")
                .isEqualTo(95);
        var vista = assertThat(atualizar(paga, "{\"vista\":true,\"revisar\":true}"))
                .hasStatusOk()
                .bodyJson();
        vista.extractingPath("$.posicaoSegundos").isEqualTo(95);
        vista.extractingPath("$.vista").isEqualTo(true);
        vista.extractingPath("$.revisar").isEqualTo(true);
        vista.extractingPath("$.vistaEm").isNotNull();

        var metade = assertThat(turma(estrutura.turma(), token)).hasStatusOk().bodyJson();
        metade.extractingPath("$.aulasVistas").isEqualTo(1);
        metade.extractingPath("$.percentual").isEqualTo(50);
        metade.extractingPath("$.aulas[*].aulaId").isEqualTo(List.of((int) paga, (int) estrutura.aulaGratuita()));
        metade.extractingPath("$.aulas[0].posicaoSegundos").isEqualTo(95);

        assertThat(atualizar(estrutura.aulaGratuita(), "{\"vista\":true}")).hasStatusOk();
        assertThat(turma(estrutura.turma(), token))
                .bodyJson()
                .extractingPath("$.percentual")
                .isEqualTo(100);

        // Desmarcar volta o percentual; aula inativada sai da conta
        assertThat(atualizar(paga, "{\"vista\":false}"))
                .bodyJson()
                .extractingPath("$.vistaEm")
                .isNull();
        helper.adm().status("/api/adm/aulas/" + paga + "/status", "INATIVO");
        var semAInativa = assertThat(turma(estrutura.turma(), token)).bodyJson();
        semAInativa.extractingPath("$.totalAulas").isEqualTo(1);
        semAInativa.extractingPath("$.percentual").isEqualTo(100);
        assertThat(progresso(paga))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Aula não encontrada.");
    }

    @Test
    @DisplayName("o progresso de um aluno nunca aparece para outro aluno da mesma turma")
    void isolamentoEntreAlunos() {
        matricularVigente(aluno.email());
        UsuarioTeste outro = criarUsuario(Papel.ALUNO);
        matricularVigente(outro.email());
        String tokenOutro = login(outro.email(), outro.senha());
        assertThat(atualizar(estrutura.aulaPaga(), "{\"posicaoSegundos\":300,\"vista\":true}"))
                .hasStatusOk();

        assertThat(chamar(HttpMethod.GET, "/api/aluno/aulas/" + estrutura.aulaPaga() + "/progresso", null, tokenOutro))
                .bodyJson()
                .isLenientlyEqualTo("{\"posicaoSegundos\":0,\"vista\":false}");
        assertThat(turma(estrutura.turma(), tokenOutro))
                .bodyJson()
                .extractingPath("$.percentual")
                .isEqualTo(0);
        assertThat(turma(estrutura.turma(), token))
                .bodyJson()
                .extractingPath("$.percentual")
                .isEqualTo(50);
    }

    static Stream<Arguments> semMatriculaVigente() {
        return Stream.of(
                Arguments.of("sem matrícula"),
                Arguments.of("vencida"),
                Arguments.of("cancelada"),
                Arguments.of("ainda não iniciada"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("semMatriculaVigente")
    @DisplayName("sem matrícula vigente: 403 na aula paga e na turma; nada é gravado")
    void semAcesso(String situacao) {
        Instant agora = Instant.now();
        switch (situacao) {
            case "vencida" ->
                helper.matricula(estrutura.turma(), aluno.email(), agora.minus(DIA.multipliedBy(2)), agora.minus(DIA));
            case "cancelada" ->
                helper.cancelar(helper.matricula(estrutura.turma(), aluno.email(), agora.minus(DIA), null));
            case "ainda não iniciada" -> helper.matricula(estrutura.turma(), aluno.email(), agora.plus(DIA), null);
            default -> {}
        }

        assertThat(progresso(estrutura.aulaPaga())).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(atualizar(estrutura.aulaPaga(), "{\"vista\":true}"))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Você não tem acesso a esta aula. Verifique o período da sua matrícula.");
        assertThat(turma(estrutura.turma(), token))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Você não tem acesso a esta turma. Verifique o período da sua matrícula.");
        assertThat(progressos.findByUsuarioIdAndAulaId(aluno.id(), estrutura.aulaPaga()))
                .isEmpty();
    }

    @Test
    @DisplayName("aula gratuita: progresso sem matrícula; a turma continua exigindo matrícula")
    void gratuita() {
        assertThat(atualizar(estrutura.aulaGratuita(), "{\"posicaoSegundos\":12}"))
                .hasStatusOk();
        assertThat(progresso(estrutura.aulaGratuita()))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.posicaoSegundos")
                .isEqualTo(12);
        assertThat(turma(estrutura.turma(), token)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("turma sem aulas fica em 0%")
    void turmaSemAulas() {
        CursoITHelper adm = helper.adm();
        String slug = stamp();
        long curso = adm.criarCurso("IT Vazio " + slug, slug);
        long turma = adm.criarTurma(curso, "Turma vazia");
        adm.ativarTurma(turma);
        adm.criarModulo(turma, "Módulo sem aulas");
        adm.publicar(curso);
        helper.matricula(turma, aluno.email(), Instant.now().minus(DIA), null);

        assertThat(turma(turma, token)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"totalAulas":0,"aulasVistas":0,"percentual":0,"aulas":[]}
                """);
    }

    @Test
    @DisplayName("anônimo recebe 401 nas três rotas")
    void anonimo() {
        String aula = "/api/aluno/aulas/" + estrutura.aulaPaga() + "/progresso";
        assertThat(chamar(HttpMethod.GET, aula, null, null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.PATCH, aula, "{\"vista\":true}", null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(turma(estrutura.turma(), null)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("corpo vazio ou posição negativa respondem 400 com os campos")
    void validacao() {
        matricularVigente(aluno.email());

        assertThat(atualizar(estrutura.aulaPaga(), "{}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .isEqualTo(List.of("algumCampo"));
        assertThat(atualizar(estrutura.aulaPaga(), "{\"posicaoSegundos\":-1}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .isEqualTo(List.of("posicaoSegundos"));
    }
}
