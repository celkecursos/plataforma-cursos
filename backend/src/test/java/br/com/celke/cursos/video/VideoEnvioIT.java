package br.com.celke.cursos.video;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.shared.armazenamento.ArmazenamentoProperties;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.video.domain.Video;
import br.com.celke.cursos.video.infra.VideoRepository;
import com.jayway.jsonpath.JsonPath;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: envio do vídeo em partes direto ao Garage real, conferência do arquivo na conclusão (tamanho e
 * assinatura), conversão para HLS com o ffmpeg de verdade (fila processada no próprio teste), troca do
 * vídeo (o HLS anterior é apagado), erro e reprocessamento, exclusão e os bloqueios durante o
 * processamento.
 * Não cobre: argumentos do ffmpeg e regras caso a caso (ComandosFfmpegTest, VarianteTest,
 * EnvioVideoServiceTest, VideoAdmServiceTest).
 * Dado criado: cursos it-<stamp>, arquivos em uploads/videos/ e videos/. Exige ffmpeg no PATH.
 */
class VideoEnvioIT extends IntegrationTest {

    /** Início de um MP4 válido seguido de lixo: passa na assinatura, mas o ffprobe recusa. */
    private static final byte[] MP4_CORROMPIDO =
            HexFormat.of().parseHex("000000206674797069736f6d" + "00".repeat(100) + "deadbeef".repeat(50));

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private StorageService storage;

    @Autowired
    private VideoRepository videos;

    @Autowired
    private ArmazenamentoProperties armazenamento;

    private CursoITHelper adm;
    private long modulo;
    private long aula;
    /** Chave do original do último envio feito por {@link #enviar}. */
    private String ultimoUpload;

    @BeforeEach
    void criarAula() {
        adm = new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA)));
        String slug = stamp();
        long curso = adm.criarCurso("IT Curso " + slug, slug);
        modulo = adm.criarModulo(adm.criarTurma(curso, "Turma"), "Módulo");
        aula = adm.criarAula(modulo, "Aula com vídeo", false);
    }

    private static byte[] amostra() throws Exception {
        try (InputStream entrada = VideoEnvioIT.class.getResourceAsStream("/amostras/video-3s.mp4")) {
            return entrada.readAllBytes();
        }
    }

    private String rota(String sufixo) {
        return "/api/adm/aulas/" + aula + "/video" + sufixo;
    }

    private MvcTestResult iniciar(String contentType, long tamanho) {
        return adm.enviar(
                "POST",
                rota("/envio"),
                "{\"nomeArquivo\":\"aula.mp4\",\"contentType\":\"%s\",\"tamanho\":%d}".formatted(contentType, tamanho));
    }

    /** Inicia o envio, faz o PUT de cada parte nas URLs pré-assinadas e pede a conclusão. */
    private MvcTestResult enviar(byte[] conteudo, String contentType) throws Exception {
        MvcTestResult inicio = iniciar(contentType, conteudo.length);
        assertThat(inicio).hasStatusOk();
        ultimoUpload = video().getUploadChave();
        String corpo = CursoITHelper.corpo(inicio);
        int tamanhoParte = JsonPath.<Number>read(corpo, "$.tamanhoParte").intValue();
        List<String> urls = JsonPath.read(corpo, "$.partes[*].url");
        StringBuilder partes = new StringBuilder();
        for (int i = 0; i < urls.size(); i++) {
            assertThat(urls.get(i)).startsWith(armazenamento.endpointPublico());
            int de = i * tamanhoParte;
            int ate = Math.min(conteudo.length, de + tamanhoParte);
            var put = http.send(
                    HttpRequest.newBuilder(URI.create(urls.get(i)))
                            .PUT(BodyPublishers.ofByteArray(conteudo, de, ate - de))
                            .build(),
                    BodyHandlers.discarding());
            assertThat(put.statusCode()).isEqualTo(200);
            partes.append(i == 0 ? "" : ",").append("{\"numero\":%d,\"etag\":\"%s\"}".formatted(i + 1, etag(put)));
        }
        return adm.enviar("POST", rota("/envio/conclusao"), "{\"partes\":[" + partes + "]}");
    }

    /** ETag da resposta do PUT, escapado para ir dentro de uma string JSON (ele vem entre aspas). */
    private static String etag(HttpResponse<?> put) {
        return put.headers().firstValue("ETag").orElseThrow().replace("\"", "\\\"");
    }

    private Video video() {
        return videos.findByAulaId(aula).orElseThrow();
    }

    private String texto(String chave) {
        return new String(storage.baixar(chave), StandardCharsets.UTF_8);
    }

    private static String prefixo(String playlist) {
        return playlist.substring(0, playlist.lastIndexOf('/') + 1);
    }

    @Test
    @DisplayName("envia em partes, converte para HLS 720p sem ampliar, troca o vídeo e remove")
    void cicloDoVideo() throws Exception {
        byte[] original = amostra();

        assertThat(enviar(original, "video/mp4"))
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("PENDENTE");
        String upload = ultimoUpload;
        assertThat(video().getUploadChave()).isEqualTo(upload);
        assertThat(upload).startsWith("uploads/videos/" + aula + "/");
        assertThat(storage.info(upload))
                .get()
                .extracting(StorageService.InfoArquivo::tamanho)
                .isEqualTo((long) original.length);

        processarFila();

        var resposta = adm.enviar("GET", rota(""), null);
        assertThat(resposta).hasStatusOk();
        var json = assertThat(resposta).bodyJson();
        json.extractingPath("$.status").isEqualTo("PRONTO");
        json.extractingPath("$.publicado").isEqualTo(true);
        json.extractingPath("$.nomeOriginal").isEqualTo("aula.mp4");
        json.extractingPath("$.tamanhoOriginal").isEqualTo(original.length);
        json.extractingPath("$.duracaoSegundos").isEqualTo(3);
        json.extractingPath("$.largura").isEqualTo(1280);
        json.extractingPath("$.altura").isEqualTo(720);
        json.extractingPath("$.variantes").isEqualTo(List.of("720p"));
        json.extractingPath("$.thumbnailUrl").asString().startsWith(armazenamento.endpointPublico());
        json.extractingPath("$.segundosProcessamentoPorMinuto").isNotNull();
        json.extractingPath("$.erro").isNull();

        Video pronto = video();
        String playlist = pronto.getPlaylistChave();
        assertThat(playlist).matches("videos/" + aula + "/\\d+/master\\.m3u8");
        assertThat(texto(playlist)).contains("720p/index.m3u8").doesNotContain("1080p");
        assertThat(texto(prefixo(playlist) + "720p/index.m3u8"))
                .contains("#EXT-X-ENDLIST")
                .contains("seg_0000.ts");
        assertThat(storage.info(prefixo(playlist) + "720p/seg_0000.ts"))
                .get()
                .extracting(StorageService.InfoArquivo::contentType)
                .isEqualTo("video/mp2t");
        assertThat(storage.info(pronto.getThumbnailChave()))
                .get()
                .extracting(StorageService.InfoArquivo::contentType)
                .isEqualTo("image/jpeg");
        assertThat(storage.info(prefixo(playlist) + "ffmpeg.log"))
                .as("log do ffmpeg não é publicado")
                .isEmpty();
        assertThat(pronto.getTamanhoFinal()).isPositive();
        assertThat(storage.info(upload))
                .as("original apagado depois da conversão")
                .isEmpty();

        // A aula com vídeo não pode ser excluída
        assertThat(adm.enviar("DELETE", "/api/adm/aulas/" + aula, null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A aula tem vídeo. Remova o vídeo ou inative a aula.");

        // Troca: o HLS anterior continua até o novo ficar pronto e então é apagado
        assertThat(enviar(original, "video/mp4")).hasStatus(HttpStatus.ACCEPTED);
        assertThat(video().getPlaylistChave()).isEqualTo(playlist);
        processarFila();
        String nova = video().getPlaylistChave();
        assertThat(nova).isNotEqualTo(playlist);
        assertThat(storage.info(nova)).isPresent();
        assertThat(storage.info(playlist)).as("HLS anterior apagado").isEmpty();
        assertThat(storage.info(prefixo(playlist) + "720p/seg_0000.ts")).isEmpty();

        // Remoção: vídeo e arquivos somem; a aula pode ser excluída
        assertThat(adm.enviar("DELETE", rota(""), null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(storage.info(nova)).isEmpty();
        assertThat(adm.enviar("GET", rota(""), null))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A aula não tem vídeo.");
        assertThat(adm.enviar("DELETE", "/api/adm/aulas/" + aula, null)).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("arquivo com extensão falsa é recusado na conclusão, apagado, e não pode ser reprocessado")
    void extensaoFalsa() throws Exception {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        assertThat(enviar(html, "video/mp4"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O arquivo não é um vídeo MP4, MOV, MKV ou WebM.");

        assertThat(ultimoUpload).startsWith("uploads/videos/" + aula + "/");
        assertThat(storage.info(ultimoUpload)).as("original recusado é apagado").isEmpty();
        assertThat(video().getUploadChave()).isNull();
        var json = assertThat(adm.enviar("GET", rota(""), null)).bodyJson();
        json.extractingPath("$.status").isEqualTo("ERRO");
        json.extractingPath("$.erro").isEqualTo("O arquivo não é um vídeo MP4, MOV, MKV ou WebM.");
        json.extractingPath("$.publicado").isEqualTo(false);
        assertThat(adm.enviar("POST", rota("/reprocessamento"), null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O arquivo original não está mais disponível. Envie o vídeo de novo.");
    }

    @Test
    @DisplayName("vídeo corrompido falha no worker sem nova tentativa; reprocessar tenta de novo a partir do original")
    void corrompidoEReprocessamento() throws Exception {
        assertThat(enviar(MP4_CORROMPIDO, "video/mp4")).hasStatus(HttpStatus.ACCEPTED);

        // Na fila: não aceita envio novo, reprocessamento nem exclusão
        assertThat(iniciar("video/mp4", 10))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O vídeo da aula está em processamento. Aguarde o fim para alterá-lo.");
        assertThat(adm.enviar("POST", rota("/reprocessamento"), null)).hasStatus(HttpStatus.CONFLICT);
        assertThat(adm.enviar("DELETE", rota(""), null)).hasStatus(HttpStatus.CONFLICT);

        processarFila();

        Video comErro = video();
        assertThat(comErro.getStatus()).hasToString("ERRO");
        assertThat(comErro.getErro()).startsWith("O arquivo não é um vídeo válido");
        assertThat(storage.info(comErro.getUploadChave()))
                .as("original guardado")
                .isPresent();

        assertThat(adm.enviar("POST", rota("/reprocessamento"), null))
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("PENDENTE");
        processarFila();
        assertThat(video().getStatus()).hasToString("ERRO");

        // Com erro, o vídeo pode ser removido (e o original vai junto)
        assertThat(adm.enviar("DELETE", rota(""), null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(storage.info(comErro.getUploadChave())).isEmpty();
    }

    @Test
    @DisplayName("tamanho real diferente do informado é recusado e apagado")
    void tamanhoDiferente() throws Exception {
        MvcTestResult inicio = iniciar("video/mp4", 5000);
        String corpo = CursoITHelper.corpo(inicio);
        String url = JsonPath.read(corpo, "$.partes[0].url");
        var put = http.send(
                HttpRequest.newBuilder(URI.create(url))
                        .PUT(BodyPublishers.ofByteArray(amostra(), 0, 100))
                        .build(),
                BodyHandlers.discarding());
        assertThat(adm.enviar(
                        "POST",
                        rota("/envio/conclusao"),
                        "{\"partes\":[{\"numero\":1,\"etag\":\"%s\"}]}".formatted(etag(put))))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O tamanho do arquivo enviado não confere com o informado. Comece um novo envio.");
        assertThat(video().getUploadChave()).isNull();
    }

    @Test
    @DisplayName("recusas antes de assinar: tipo, tamanho, validação, aula inexistente e conclusão sem envio")
    void recusas() {
        assertThat(iniciar("video/x-msvideo", 10))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Envie um vídeo MP4, MOV, MKV ou WebM.");
        assertThat(iniciar("video/mp4", 5L * 1024 * 1024 * 1024 + 1))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O vídeo pode ter no máximo 5 GB.");
        assertThat(adm.enviar("POST", rota("/envio"), "{\"contentType\":\"video/mp4\",\"tamanho\":0}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .isEqualTo(List.of("nomeArquivo", "tamanho"));
        assertThat(adm.enviar(
                        "POST",
                        "/api/adm/aulas/999999999/video/envio",
                        "{\"nomeArquivo\":\"a.mp4\",\"contentType\":\"video/mp4\",\"tamanho\":10}"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Aula não encontrada.");
        assertThat(adm.enviar("POST", rota("/envio/conclusao"), "{\"partes\":[{\"numero\":1,\"etag\":\"x\"}]}"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Nenhum envio em andamento para esta aula. Comece um novo envio.");
        assertThat(videos.findByAulaId(aula)).isEmpty();
    }
}
