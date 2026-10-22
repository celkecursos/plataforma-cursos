package br.com.celke.cursos.certificado;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.certificado.infra.CertificadoRepository;
import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.matricula.MatriculaITHelper;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: certificado — situação e regra de conclusão da turma (padrão 100% e configurada pelo ADM),
 * emissão com o nome confirmado, PDF no Garage, emissão repetida devolvendo o mesmo, validação pública
 * pelo código (sem dados internos), download só do próprio aluno, aluno sem matrícula, matriz de papéis
 * da regra no ADM e validação do corpo.
 * Não cobre: bordas da regra e do nome (CertificadoDominioTest, CertificadoServiceTest).
 * Dado criado: curso it-<stamp> publicado com duas aulas e alunos próprios.
 */
class CertificadoIT extends IntegrationTest {

    private static final Duration DIA = Duration.ofDays(1);

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private CertificadoRepository certificados;

    @Autowired
    private StorageService storage;

    private MatriculaITHelper helper;
    private MatriculaITHelper.Estrutura estrutura;
    private UsuarioTeste aluno;
    private String token;

    @BeforeEach
    void criarTurmaComAluno() {
        helper = new MatriculaITHelper(
                new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA))));
        estrutura = helper.cursoPublicado(stamp());
        aluno = criarUsuario(Papel.ALUNO);
        Instant agora = Instant.now();
        helper.matricula(estrutura.turma(), aluno.email(), agora.minus(DIA), agora.plus(DIA));
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

    private String rota() {
        return "/api/aluno/turmas/" + estrutura.turma() + "/certificado";
    }

    private MvcTestResult emitir(String nome, String tokenUsado) {
        return chamar(HttpMethod.POST, rota(), "{\"nomeCompleto\":\"%s\"}".formatted(nome), tokenUsado);
    }

    private void marcarVista(long aula) {
        assertThat(chamar(HttpMethod.PATCH, "/api/aluno/aulas/" + aula + "/progresso", "{\"vista\":true}", token))
                .hasStatusOk();
    }

    @Test
    @DisplayName("conclui a turma, emite com o nome confirmado, valida pelo código e baixa o PDF")
    void cicloDoCertificado() throws Exception {
        assertThat(chamar(HttpMethod.GET, rota(), null, token))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                {"percentualVisto":0,"percentualExigido":100,"concluiu":false,"certificado":null}
                """);
        assertThat(emitir("Ana Souza", token))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Você ainda não concluiu a turma: 0% das aulas vistas, de 100% exigidos.");

        marcarVista(estrutura.aulaPaga());
        assertThat(emitir("Ana Souza", token)).hasStatus(HttpStatus.CONFLICT);
        marcarVista(estrutura.aulaGratuita());

        MvcTestResult emitido = emitir("  Ana   Souza  ", token);
        assertThat(emitido).hasStatus(HttpStatus.CREATED);
        String codigo = JsonPath.read(CursoITHelper.corpo(emitido), "$.codigo");
        assertThat(codigo).matches("[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}");
        assertThat(emitido.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/certificados/" + codigo);
        assertThat(emitido).bodyJson().extractingPath("$.nomeCompleto").isEqualTo("Ana Souza");

        // PDF guardado no bucket
        var certificado = certificados.findByCodigo(codigo).orElseThrow();
        byte[] pdf = storage.baixar(certificado.getPdfChave());
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");

        // Validação pública, sem token e sem dados internos
        MvcTestResult validacao = chamar(HttpMethod.GET, "/api/certificados/" + codigo.toLowerCase(), null, null);
        assertThat(validacao).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"codigo":"%s","nomeCompleto":"Ana Souza","turmaNome":"Turma"}
                """.formatted(codigo));
        assertThat(CursoITHelper.corpo(validacao))
                .doesNotContain("email")
                .doesNotContain("usuarioId")
                .doesNotContain("pdf");

        // Emitir de novo devolve o mesmo, sem trocar o nome
        assertThat(emitir("Outro Nome", token))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.codigo")
                .isEqualTo(codigo);
        assertThat(chamar(HttpMethod.GET, rota(), null, token))
                .bodyJson()
                .extractingPath("$.certificado.codigo")
                .isEqualTo(codigo);

        // Meus certificados e download
        assertThat(chamar(HttpMethod.GET, "/api/aluno/certificados", null, token))
                .bodyJson()
                .extractingPath("$[*].codigo")
                .isEqualTo(List.of(codigo));
        long id = certificado.getId();
        String url = JsonPath.read(
                CursoITHelper.corpo(chamar(HttpMethod.GET, "/api/aluno/certificados/" + id + "/download", null, token)),
                "$.url");
        HttpResponse<byte[]> baixado =
                http.send(HttpRequest.newBuilder(URI.create(url)).build(), BodyHandlers.ofByteArray());
        assertThat(baixado.statusCode()).isEqualTo(200);
        assertThat(baixado.body()).isEqualTo(pdf);
        assertThat(baixado.headers().firstValue("Content-Disposition"))
                .contains("attachment; filename=\"certificado-" + codigo + ".pdf\"");

        // Outro aluno não baixa o certificado deste
        UsuarioTeste outro = criarUsuario(Papel.ALUNO);
        assertThat(chamar(
                        HttpMethod.GET,
                        "/api/aluno/certificados/" + id + "/download",
                        null,
                        login(outro.email(), outro.senha())))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("regra configurada pelo ADM: 50% e carga horária impressa")
    void regraDaTurma() {
        assertThat(helper.adm()
                        .enviar(
                                "PUT",
                                "/api/adm/turmas/" + estrutura.turma() + "/certificado",
                                "{\"percentualConclusao\":50,\"cargaHorariaHoras\":12}"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"percentualConclusao\":50,\"cargaHorariaHoras\":12}");

        marcarVista(estrutura.aulaPaga());

        assertThat(chamar(HttpMethod.GET, rota(), null, token))
                .bodyJson()
                .isLenientlyEqualTo("{\"percentualVisto\":50,\"percentualExigido\":50,\"concluiu\":true}");
        assertThat(emitir("Bruno Lima", token))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.cargaHorariaHoras")
                .isEqualTo(12);
    }

    @Test
    @DisplayName("regra no ADM: 401 anônimo, 403 aluno, 400 fora de 1 a 100")
    void regraAcesso() {
        String uri = "/api/adm/turmas/" + estrutura.turma() + "/certificado";
        String corpo = "{\"percentualConclusao\":80}";

        assertThat(chamar(HttpMethod.PUT, uri, corpo, null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.PUT, uri, corpo, token)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(helper.adm().enviar("PUT", uri, "{\"percentualConclusao\":0}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .isEqualTo(List.of("percentualConclusao"));
    }

    @Test
    @DisplayName("sem matrícula vigente: 403 na situação e na emissão; anônimo 401")
    void semMatricula() {
        UsuarioTeste semMatricula = criarUsuario(Papel.ALUNO);
        String outro = login(semMatricula.email(), semMatricula.senha());

        assertThat(chamar(HttpMethod.GET, rota(), null, outro)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(emitir("Ana Souza", outro)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(chamar(HttpMethod.GET, rota(), null, null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.GET, "/api/aluno/certificados", null, null))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("validação: nome em branco 400; nome sem sobrenome 400; código inexistente 404")
    void validacoes() {
        marcarVista(estrutura.aulaPaga());
        marcarVista(estrutura.aulaGratuita());

        assertThat(chamar(HttpMethod.POST, rota(), "{}", token))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .isEqualTo(List.of("nomeCompleto"));
        assertThat(emitir("Ana", token))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Informe o nome completo como deve sair no certificado.");
        assertThat(chamar(HttpMethod.GET, "/api/certificados/ZZZZ-ZZZZ-ZZZZ", null, null))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Certificado não encontrado.");
        assertThat(chamar(HttpMethod.POST, "/api/certificados/ZZZZ-ZZZZ-ZZZZ", "{}", null))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
