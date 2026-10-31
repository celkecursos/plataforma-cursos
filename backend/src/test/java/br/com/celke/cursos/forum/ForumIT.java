package br.com.celke.cursos.forum;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.matricula.MatriculaITHelper;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.support.fixtures.ImagemFixture;
import br.com.celke.cursos.usuario.domain.Papel;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
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
 * Cobre: fórum da turma — pergunta e resposta do instrutor, HTML filtrado (XSS guardado não volta),
 * anexo de imagem (processado, sem EXIF) e IDOR de anexo, aluno sem matrícula, moderação (ocultar,
 * fixar, ocultar resposta), aula de outra turma, limite de mensagens (429) e validação.
 * Não cobre: o filtro de Markdown caso a caso (MarkdownSeguroTest) e a matriz de papéis do ADM
 * (ForumAcessoIT).
 * Dado criado: curso it-<stamp> publicado e alunos próprios, matriculados na turma.
 */
class ForumIT extends IntegrationTest {

    private static final Duration DIA = Duration.ofDays(1);

    private final HttpClient http = HttpClient.newHttpClient();

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
        aluno = matriculado();
        token = login(aluno.email(), aluno.senha());
    }

    private UsuarioTeste matriculado() {
        UsuarioTeste novo = criarUsuario(Papel.ALUNO);
        Instant agora = Instant.now();
        helper.matricula(estrutura.turma(), novo.email(), agora.minus(DIA), agora.plus(DIA));
        return novo;
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

    private String forum() {
        return "/api/aluno/turmas/" + estrutura.turma() + "/forum";
    }

    private MvcTestResult perguntar(String corpoJson, String tokenUsado) {
        return chamar(HttpMethod.POST, forum(), corpoJson, tokenUsado);
    }

    private long perguntaSimples(String titulo) {
        return CursoITHelper.idCriado(perguntar("{\"titulo\":\"%s\",\"corpo\":\"Corpo\"}".formatted(titulo), token));
    }

    private MvcTestResult adm(String metodo, String uri, String corpo) {
        return helper.adm().enviar(metodo, uri, corpo);
    }

    /** Envia uma imagem PNG pela URL pré-assinada do fórum e devolve a chave. */
    private String enviarImagem(String tokenUsado) throws Exception {
        byte[] png = ImagemFixture.png(40, 30);
        String corpo = CursoITHelper.corpo(chamar(
                HttpMethod.POST,
                "/api/aluno/forum/anexos/envio",
                "{\"contentType\":\"image/png\",\"tamanho\":%d}".formatted(png.length),
                tokenUsado));
        var put = http.send(
                HttpRequest.newBuilder(URI.create(JsonPath.read(corpo, "$.url")))
                        .header("Content-Type", "image/png")
                        .PUT(BodyPublishers.ofByteArray(png))
                        .build(),
                BodyHandlers.discarding());
        assertThat(put.statusCode()).isEqualTo(200);
        return JsonPath.read(corpo, "$.chave");
    }

    @Test
    @DisplayName("aluno pergunta na aula (com XSS filtrado), instrutor responde, aluno vê a resposta")
    void perguntaERespostaDoInstrutor() {
        MvcTestResult pergunta = perguntar("""
                {"titulo":"Erro no exemplo","aulaId":%d,
                 "corpo":"Recebo **erro**: <script>alert(1)</script> [veja](javascript:alert(2))"}
                """.formatted(estrutura.aulaPaga()), token);
        assertThat(pergunta).hasStatus(HttpStatus.CREATED);
        long topico = CursoITHelper.idCriado(pergunta);
        var json = assertThat(pergunta).bodyJson();
        json.extractingPath("$.aulaId").isEqualTo((int) estrutura.aulaPaga());
        json.extractingPath("$.corpoHtml")
                .asString()
                .contains("<strong>erro</strong>")
                .doesNotContain("<script")
                .doesNotContain("javascript:");
        json.extractingPath("$.respondidoInstrutor").isEqualTo(false);
        assertThat(CursoITHelper.corpo(pergunta))
                .doesNotContain("corpoMarkdown")
                .doesNotContain("\"oculto\":false");

        assertThat(chamar(HttpMethod.GET, forum() + "?aulaId=" + estrutura.aulaPaga(), null, token))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.itens[*].id")
                .isEqualTo(List.of((int) topico));

        assertThat(adm("POST", "/api/adm/forum/topicos/" + topico + "/respostas", "{\"corpo\":\"Use `final`.\"}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.respondidoInstrutor")
                .isEqualTo(true);

        var visto = assertThat(chamar(HttpMethod.GET, "/api/aluno/forum/topicos/" + topico, null, token))
                .hasStatusOk()
                .bodyJson();
        visto.extractingPath("$.respostas[0].instrutor").isEqualTo(true);
        visto.extractingPath("$.respostas[0].corpoHtml").asString().contains("<code>final</code>");
        visto.extractingPath("$.respostas[0].autorNome").isNotNull();

        // Outro aluno da turma responde; a lista mostra 2 respostas
        UsuarioTeste colega = matriculado();
        assertThat(chamar(
                        HttpMethod.POST,
                        "/api/aluno/forum/topicos/" + topico + "/respostas",
                        "{\"corpo\":\"Também tive.\"}",
                        login(colega.email(), colega.senha())))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.respostas[1].instrutor")
                .isEqualTo(false);
        assertThat(chamar(HttpMethod.GET, forum(), null, token))
                .bodyJson()
                .extractingPath("$.itens[0].respostas")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("anexo: imagem do próprio aluno vira JPEG sem EXIF; a de outro aluno é recusada (IDOR)")
    void anexoEIdor() throws Exception {
        String minha = enviarImagem(token);
        MvcTestResult pergunta = perguntar(
                "{\"titulo\":\"Print do erro\",\"corpo\":\"Segue\",\"anexos\":[\"%s\"]}".formatted(minha), token);
        assertThat(pergunta).hasStatus(HttpStatus.CREATED);
        assertThat(storage.info(minha)).as("envio original apagado").isEmpty();
        String url = JsonPath.read(CursoITHelper.corpo(pergunta), "$.anexos[0].url");
        HttpResponse<byte[]> imagem =
                http.send(HttpRequest.newBuilder(URI.create(url)).build(), BodyHandlers.ofByteArray());
        assertThat(imagem.statusCode()).isEqualTo(200);
        assertThat(imagem.headers().firstValue("Content-Type")).contains("image/jpeg");
        assertThat(imagem.body()).startsWith((byte) 0xFF, (byte) 0xD8);

        // Outro aluno tenta anexar a imagem enviada por este aluno
        String alheia = enviarImagem(token);
        UsuarioTeste outro = matriculado();
        assertThat(perguntar(
                        "{\"titulo\":\"Roubo\",\"corpo\":\"x\",\"anexos\":[\"%s\"]}".formatted(alheia),
                        login(outro.email(), outro.senha())))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Anexo inválido. Envie a imagem de novo.");
        assertThat(storage.info(alheia)).as("o envio do dono continua lá").isPresent();
    }

    @Test
    @DisplayName("aluno sem matrícula vigente: 403 para listar, perguntar, ler e responder")
    void semMatricula() {
        long topico = perguntaSimples("Dúvida");
        UsuarioTeste semMatricula = criarUsuario(Papel.ALUNO);
        String outro = login(semMatricula.email(), semMatricula.senha());

        assertThat(chamar(HttpMethod.GET, forum(), null, outro)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(perguntar("{\"titulo\":\"T\",\"corpo\":\"C\"}", outro))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Você não tem acesso a esta turma. Verifique o período da sua matrícula.");
        assertThat(chamar(HttpMethod.GET, "/api/aluno/forum/topicos/" + topico, null, outro))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(chamar(
                        HttpMethod.POST,
                        "/api/aluno/forum/topicos/" + topico + "/respostas",
                        "{\"corpo\":\"x\"}",
                        outro))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("moderação: tópico oculto some para o aluno, fixado vai para o topo, resposta oculta some")
    void moderacao() {
        long primeiro = perguntaSimples("Primeiro");
        long segundo = perguntaSimples("Segundo");
        long terceiro = perguntaSimples("Terceiro");
        String respostaId = JsonPath.read(
                        CursoITHelper.corpo(chamar(
                                HttpMethod.POST,
                                "/api/aluno/forum/topicos/" + segundo + "/respostas",
                                "{\"corpo\":\"Resposta indevida\"}",
                                token)),
                        "$.respostas[0].id")
                .toString();

        assertThat(adm("PATCH", "/api/adm/forum/topicos/" + primeiro, "{\"fixado\":true}"))
                .hasStatusOk();
        assertThat(adm("PATCH", "/api/adm/forum/topicos/" + terceiro, "{\"oculto\":true}"))
                .bodyJson()
                .extractingPath("$.oculto")
                .isEqualTo(true);
        assertThat(adm("PATCH", "/api/adm/forum/respostas/" + respostaId, "{\"oculto\":true}"))
                .hasStatusOk();

        assertThat(chamar(HttpMethod.GET, forum(), null, token))
                .bodyJson()
                .extractingPath("$.itens[*].id")
                .isEqualTo(List.of((int) primeiro, (int) segundo));
        assertThat(chamar(HttpMethod.GET, "/api/aluno/forum/topicos/" + terceiro, null, token))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Tópico não encontrado.");
        assertThat(chamar(HttpMethod.GET, "/api/aluno/forum/topicos/" + segundo, null, token))
                .bodyJson()
                .extractingPath("$.respostas")
                .isEqualTo(List.of());

        // A moderação continua vendo tudo
        assertThat(adm("GET", "/api/adm/turmas/" + estrutura.turma() + "/forum", null))
                .bodyJson()
                .extractingPath("$.itens[*].oculto")
                .isEqualTo(List.of(false, true, false));
        assertThat(adm("GET", "/api/adm/forum/topicos/" + segundo, null))
                .bodyJson()
                .extractingPath("$.respostas[0].oculto")
                .isEqualTo(true);
    }

    @Test
    @DisplayName("limite: a sexta mensagem na janela responde 429 com Retry-After")
    void limiteDeMensagens() {
        for (int i = 1; i <= 5; i++) {
            perguntaSimples("Pergunta " + i);
        }

        MvcTestResult excedeu = perguntar("{\"titulo\":\"Sexta\",\"corpo\":\"C\"}", token);

        assertThat(excedeu).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(excedeu.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
    }

    @Test
    @DisplayName("aula de outra turma: 404; corpo inválido: 400 com os campos")
    void recusas() {
        String outroSlug = stamp();
        var outra = helper.cursoPublicado(outroSlug);

        assertThat(perguntar("{\"titulo\":\"T\",\"corpo\":\"C\",\"aulaId\":%d}".formatted(outra.aulaPaga()), token))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Aula não encontrada.");
        assertThat(perguntar("{\"titulo\":\" \",\"corpo\":\"\"}", token))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .isEqualTo(List.of("corpo", "titulo"));
    }

    @Test
    @DisplayName("rotas do aluno exigem token")
    void anonimo() {
        long topico = perguntaSimples("Dúvida");

        assertThat(chamar(HttpMethod.GET, forum(), null, null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(perguntar("{\"titulo\":\"T\",\"corpo\":\"C\"}", null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.GET, "/api/aluno/forum/topicos/" + topico, null, null))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.POST, "/api/aluno/forum/anexos/envio", "{}", null))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
