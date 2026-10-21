package br.com.celke.cursos.shared.armazenamento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.shared.armazenamento.StorageService.InfoArquivo;
import br.com.celke.cursos.shared.armazenamento.StorageService.ParteEnviada;
import br.com.celke.cursos.support.Containers;
import br.com.celke.cursos.support.IntegrationTest;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.LifecycleRule;

/**
 * Cobre: upload e download por URL pré-assinada, upload em partes, consulta e exclusão no Garage
 * real, e a configuração do bucket aplicada pela API ao subir (CORS e expiração de uploads).
 * Não cobre: regras de quem pode enviar ou baixar (módulos de vídeo e arquivos).
 * Dado criado: arquivos em uploads/it-<stamp>/.
 */
class StorageServiceIT extends IntegrationTest {

    private static final Duration VALIDADE = Duration.ofMinutes(5);
    private static final int CINCO_MB = 5 * 1024 * 1024;

    private final HttpClient http = HttpClient.newHttpClient();
    private final String pasta = "uploads/" + stamp() + "/";

    @Autowired
    private StorageService storage;

    @Autowired
    private S3Client s3;

    @Autowired
    private ArmazenamentoProperties props;

    @Test
    @DisplayName("envia por PUT pré-assinado, consulta, baixa por GET pré-assinado e exclui")
    void cicloDeUmArquivo() throws Exception {
        String chave = pasta + "texto.txt";
        byte[] conteudo = "conteúdo do arquivo".getBytes(StandardCharsets.UTF_8);

        URL urlUpload = storage.urlUpload(chave, "text/plain", VALIDADE);
        assertThat(urlUpload.toString()).startsWith(props.endpointPublico());
        assertThat(put(urlUpload, conteudo, "text/plain").statusCode()).isEqualTo(200);

        assertThat(storage.info(chave)).contains(new InfoArquivo(conteudo.length, "text/plain"));

        HttpResponse<byte[]> download = http.send(
                HttpRequest.newBuilder(storage.urlDownload(chave, VALIDADE).toURI())
                        .build(),
                BodyHandlers.ofByteArray());
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(conteudo);

        storage.excluir(chave);
        assertThat(storage.info(chave)).isEmpty();
    }

    @Test
    @DisplayName("URL de upload não aceita conteúdo com tipo diferente do assinado")
    void uploadComTipoDiferente() throws Exception {
        URL url = storage.urlUpload(pasta + "imagem.png", "image/png", VALIDADE);
        assertThat(put(url, new byte[] {1, 2, 3}, "text/html").statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("upload em partes junta as partes na ordem certa")
    void uploadEmPartes() throws Exception {
        String chave = pasta + "video.mp4";
        byte[] parte1 = new byte[CINCO_MB];
        Arrays.fill(parte1, (byte) 1);
        byte[] parte2 = "fim".getBytes(StandardCharsets.UTF_8);

        String uploadId = storage.iniciarMultipart(chave, "video/mp4");
        // Envia fora de ordem: a conclusão ordena pelo número da parte
        String etag2 = put(storage.urlParte(chave, uploadId, 2, VALIDADE), parte2, null)
                .headers()
                .firstValue("ETag")
                .orElseThrow();
        String etag1 = put(storage.urlParte(chave, uploadId, 1, VALIDADE), parte1, null)
                .headers()
                .firstValue("ETag")
                .orElseThrow();
        storage.concluirMultipart(chave, uploadId, List.of(new ParteEnviada(2, etag2), new ParteEnviada(1, etag1)));

        assertThat(storage.info(chave)).hasValueSatisfying(info -> {
            assertThat(info.tamanho()).isEqualTo(CINCO_MB + parte2.length);
            assertThat(info.contentType()).isEqualTo("video/mp4");
        });
    }

    @Test
    @DisplayName("upload em partes abortado não deixa arquivo")
    void uploadEmPartesAbortado() throws Exception {
        String chave = pasta + "abortado.mp4";
        String uploadId = storage.iniciarMultipart(chave, "video/mp4");
        put(storage.urlParte(chave, uploadId, 1, VALIDADE), "parte".getBytes(StandardCharsets.UTF_8), null);

        storage.abortarMultipart(chave, uploadId);

        assertThat(storage.info(chave)).isEmpty();
    }

    @Test
    @DisplayName("consulta de arquivo inexistente devolve vazio")
    void arquivoInexistente() {
        assertThat(storage.info(pasta + "nao-existe.txt")).isEmpty();
    }

    @Test
    @DisplayName("bucket fica com CORS para o front, expondo o ETag")
    void corsDoBucket() {
        List<CORSRule> regras =
                s3.getBucketCors(r -> r.bucket(Containers.S3_BUCKET)).corsRules();
        assertThat(regras).singleElement().satisfies(regra -> {
            assertThat(regra.allowedOrigins()).containsExactly("http://localhost:5173");
            assertThat(regra.allowedMethods()).containsExactlyInAnyOrder("GET", "PUT", "HEAD");
            assertThat(regra.exposeHeaders()).contains("ETag");
        });
    }

    @Test
    @DisplayName("preflight do navegador é aceito só para a origem configurada")
    void preflightCors() throws Exception {
        URI url = storage.urlUpload(pasta + "cors.txt", "text/plain", VALIDADE).toURI();

        HttpResponse<Void> permitida = preflight(url, "http://localhost:5173");
        assertThat(permitida.statusCode()).isEqualTo(200);
        assertThat(permitida.headers().firstValue("Access-Control-Allow-Origin"))
                .contains("http://localhost:5173");
        assertThat(permitida.headers().firstValue("Access-Control-Allow-Methods"))
                .hasValueSatisfying(metodos -> assertThat(metodos).contains("PUT"));

        // Origem não configurada: o preflight é recusado e o navegador não envia o PUT. O Garage
        // põe "Access-Control-Allow-Origin: *" só para o navegador conseguir ler a resposta de erro.
        HttpResponse<Void> negada = preflight(url, "https://site-estranho.com");
        assertThat(negada.statusCode()).isEqualTo(403);
        assertThat(negada.headers().firstValue("Access-Control-Allow-Methods")).isEmpty();
    }

    @Test
    @DisplayName("bucket expira o que fica em uploads/ e os uploads em partes incompletos")
    void expiracaoDeUploads() {
        List<LifecycleRule> regras = s3.getBucketLifecycleConfiguration(r -> r.bucket(Containers.S3_BUCKET))
                .rules();
        assertThat(regras).singleElement().satisfies(regra -> {
            assertThat(regra.filter().prefix()).isEqualTo("uploads/");
            assertThat(regra.expiration().days()).isEqualTo(2);
            assertThat(regra.abortIncompleteMultipartUpload().daysAfterInitiation())
                    .isEqualTo(1);
        });
    }

    private HttpResponse<Void> put(URL url, byte[] corpo, String contentType) throws Exception {
        HttpRequest.Builder requisicao = HttpRequest.newBuilder(url.toURI()).PUT(BodyPublishers.ofByteArray(corpo));
        if (contentType != null) {
            requisicao.header("Content-Type", contentType);
        }
        return http.send(requisicao.build(), BodyHandlers.discarding());
    }

    private HttpResponse<Void> preflight(URI url, String origem) throws Exception {
        return http.send(
                HttpRequest.newBuilder(url)
                        .method("OPTIONS", BodyPublishers.noBody())
                        .header("Origin", origem)
                        .header("Access-Control-Request-Method", "PUT")
                        .build(),
                BodyHandlers.discarding());
    }
}
