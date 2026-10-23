package br.com.celke.cursos.video;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.matricula.MatriculaITHelper;
import br.com.celke.cursos.shared.armazenamento.ArmazenamentoProperties;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.video.application.LogAcessoVideoService;
import br.com.celke.cursos.video.domain.AcessoVideo;
import br.com.celke.cursos.video.infra.AcessoVideoRepository;
import com.jayway.jsonpath.JsonPath;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: playlists HLS entregues ao aluno — matriz de acesso ao conteúdo (vigente, sem matrícula,
 * vencida, cancelada, ainda não iniciada, aula gratuita), anônimo, master apontando para a API,
 * variante com segmentos pré-assinados (validade = duração + margem) que baixam do Garage, aula sem
 * vídeo, variante inexistente, log de acesso e a retenção dele.
 * Não cobre: reescrita linha a linha e regras de validade (ReescritaPlaylistTest, PlaylistServiceTest);
 * a expiração real da URL (VideoE2E, com margem curta).
 * Dado criado: curso it-<stamp> publicado com o vídeo de amostra convertido na aula paga e um aluno
 * próprio. Exige ffmpeg no PATH.
 */
class PlaylistIT extends IntegrationTest {

    private static final Duration DIA = Duration.ofDays(1);

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private AcessoVideoRepository acessos;

    @Autowired
    private LogAcessoVideoService logAcessoVideoService;

    @Autowired
    private ArmazenamentoProperties armazenamento;

    private MatriculaITHelper helper;
    private MatriculaITHelper.Estrutura estrutura;
    private UsuarioTeste aluno;
    private String token;

    @BeforeEach
    void criarCursoComVideo() throws Exception {
        helper = new MatriculaITHelper(
                new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA))));
        estrutura = helper.cursoPublicado(stamp());
        enviarVideo(estrutura.aulaPaga());
        aluno = criarUsuario(Papel.ALUNO);
        token = login(aluno.email(), aluno.senha());
    }

    /** Envia o vídeo de amostra pela API do ADM (uma parte) e processa a fila: o vídeo fica pronto. */
    private void enviarVideo(long aula) throws Exception {
        byte[] video;
        try (InputStream entrada = getClass().getResourceAsStream("/amostras/video-3s.mp4")) {
            video = entrada.readAllBytes();
        }
        String rota = "/api/adm/aulas/" + aula + "/video";
        String corpo = CursoITHelper.corpo(helper.adm()
                .enviar(
                        "POST",
                        rota + "/envio",
                        "{\"nomeArquivo\":\"a.mp4\",\"contentType\":\"video/mp4\",\"tamanho\":%d}"
                                .formatted(video.length)));
        String url = JsonPath.read(corpo, "$.partes[0].url");
        var put = http.send(
                HttpRequest.newBuilder(URI.create(url))
                        .PUT(BodyPublishers.ofByteArray(video))
                        .build(),
                BodyHandlers.discarding());
        String etag = put.headers().firstValue("ETag").orElseThrow().replace("\"", "\\\"");
        assertThat(helper.adm()
                        .enviar(
                                "POST",
                                rota + "/envio/conclusao",
                                "{\"partes\":[{\"numero\":1,\"etag\":\"%s\"}]}".formatted(etag)))
                .hasStatus(HttpStatus.ACCEPTED);
        processarFila();
        assertThat(helper.adm().enviar("GET", rota, null))
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("PRONTO");
    }

    private MvcTestResult get(String uri, String tokenUsado) {
        var requisicao = mvc.get().uri(uri);
        if (tokenUsado != null) {
            requisicao = requisicao.header(HttpHeaders.AUTHORIZATION, bearer(tokenUsado));
        }
        return requisicao.exchange();
    }

    private MvcTestResult master(long aula) {
        return get("/api/aulas/" + aula + "/video.m3u8", token);
    }

    private MvcTestResult variante(long aula, String variante) {
        return get("/api/aulas/" + aula + "/video/" + variante + ".m3u8", token);
    }

    private void matricularVigente() {
        Instant agora = Instant.now();
        helper.matricula(estrutura.turma(), aluno.email(), agora.minus(DIA), agora.plus(DIA));
    }

    private List<AcessoVideo> log(long aula) {
        return acessos.findByUsuarioIdAndAulaIdOrderByAcessadoEm(aluno.id(), aula);
    }

    @Test
    @DisplayName("matrícula vigente: master aponta para a API e a variante traz segmentos assinados que baixam")
    void vigente() throws Exception {
        matricularVigente();

        MvcTestResult master = master(estrutura.aulaPaga());
        assertThat(master).hasStatusOk();
        assertThat(master.getResponse().getContentType()).startsWith("application/vnd.apple.mpegurl");
        assertThat(master.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        String textoMaster = master.getResponse().getContentAsString();
        assertThat(textoMaster)
                .startsWith("#EXTM3U")
                .contains("\nvideo/720p.m3u8\n")
                .doesNotContain("index.m3u8");

        MvcTestResult variante = variante(estrutura.aulaPaga(), "720p");
        assertThat(variante).hasStatusOk();
        assertThat(variante.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        List<String> segmentos = variante.getResponse()
                .getContentAsString()
                .lines()
                .filter(l -> !l.startsWith("#") && !l.isBlank())
                .toList();
        assertThat(segmentos)
                .isNotEmpty()
                .allSatisfy(url -> assertThat(url)
                        .startsWith(armazenamento.endpointPublico())
                        .contains("/720p/seg_")
                        // 3 s de vídeo + 2 h de margem
                        .contains("X-Amz-Expires=7203"));

        HttpResponse<byte[]> segmento = http.send(
                HttpRequest.newBuilder(URI.create(segmentos.getFirst())).build(), BodyHandlers.ofByteArray());
        assertThat(segmento.statusCode()).isEqualTo(200);
        assertThat(segmento.body()).isNotEmpty();
        // Pacote MPEG-TS começa com o byte de sincronização 0x47
        assertThat(segmento.body()[0]).isEqualTo((byte) 0x47);

        // Só a abertura da master entra no log de acesso
        assertThat(log(estrutura.aulaPaga())).singleElement().satisfies(acesso -> {
            assertThat(acesso.getIp()).isNotBlank();
            assertThat(acesso.getAcessadoEm()).isAfter(Instant.now().minusSeconds(60));
        });
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
    @DisplayName("sem matrícula vigente: 403 na master e na variante, sem registro no log")
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

        assertThat(master(estrutura.aulaPaga()))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Você não tem acesso a esta aula. Verifique o período da sua matrícula.");
        assertThat(variante(estrutura.aulaPaga(), "720p")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(log(estrutura.aulaPaga())).isEmpty();
    }

    @Test
    @DisplayName("aula gratuita: assiste sem matrícula")
    void gratuita() throws Exception {
        enviarVideo(estrutura.aulaGratuita());

        assertThat(master(estrutura.aulaGratuita())).hasStatusOk();
        assertThat(variante(estrutura.aulaGratuita(), "720p")).hasStatusOk();
        assertThat(log(estrutura.aulaGratuita())).hasSize(1);
    }

    @Test
    @DisplayName("anônimo recebe 401")
    void anonimo() {
        assertThat(get("/api/aulas/" + estrutura.aulaPaga() + "/video.m3u8", null))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/aulas/" + estrutura.aulaPaga() + "/video/720p.m3u8", null))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("aula sem vídeo e variante que o vídeo não tem respondem 404")
    void naoEncontrados() {
        matricularVigente();

        assertThat(master(estrutura.aulaGratuita()))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A aula não tem vídeo.");
        // A amostra é 720p: não há 1080p (nunca aumenta a resolução)
        assertThat(variante(estrutura.aulaPaga(), "1080p"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Variante não encontrada.");
        assertThat(get("/api/aulas/999999999/video.m3u8", token))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Aula não encontrada.");
    }

    @Test
    @DisplayName("retenção do log: registros com mais de 180 dias são apagados, os recentes ficam")
    void retencao() {
        Instant agora = Instant.now();
        acessos.save(new AcessoVideo(
                aluno.id(), estrutura.aulaPaga(), "203.0.113.1", "antigo", agora.minus(Duration.ofDays(181))));
        acessos.save(new AcessoVideo(
                aluno.id(), estrutura.aulaPaga(), "203.0.113.2", "recente", agora.minus(Duration.ofDays(179))));

        assertThat(logAcessoVideoService.apagarVencidos()).isPositive();

        assertThat(log(estrutura.aulaPaga()))
                .extracting(AcessoVideo::getUserAgent)
                .containsExactly("recente");
    }
}
