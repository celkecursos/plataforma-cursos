package br.com.celke.cursos.curso;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.infra.CursoRepository;
import br.com.celke.cursos.shared.armazenamento.ArmazenamentoProperties;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.support.fixtures.ImagemFixture;
import com.jayway.jsonpath.JsonPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * Cobre: capa do curso com o Garage real — envio por URL pré-assinada, confirmação que reduz e
 * regrava em JPEG sem EXIF, troca (apaga a anterior), remoção, redirecionamento do catálogo e os
 * envios recusados (tipo, tamanho, conteúdo, chave de outro curso), sempre apagando o original.
 * Não cobre: casos do processamento de imagem (ProcessadorImagemTest).
 * Dado criado: cursos it-<stamp> e arquivos em uploads/capas/ e capas/.
 */
class CapaCursoIT extends IntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private StorageService storage;

    @Autowired
    private CursoRepository cursos;

    @Autowired
    private ArmazenamentoProperties armazenamento;

    private CursoITHelper adm;
    private long curso;
    private String slug;

    @BeforeEach
    void criarCurso() {
        adm = new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA)));
        slug = stamp();
        curso = adm.criarCurso("IT Curso " + slug, slug);
    }

    /** Inicia o envio, faz o PUT da imagem na URL pré-assinada e devolve a chave. */
    private String enviar(byte[] conteudo, String contentType) throws Exception {
        var inicio = adm.enviar(
                "POST",
                "/api/adm/cursos/" + curso + "/capa/envio",
                "{\"contentType\":\"%s\",\"tamanho\":%d}".formatted(contentType, conteudo.length));
        assertThat(inicio).hasStatusOk();
        String corpo = CursoITHelper.corpo(inicio);
        String url = JsonPath.read(corpo, "$.url");
        assertThat(url).startsWith(armazenamento.endpointPublico());
        var put = http.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("Content-Type", contentType)
                        .PUT(BodyPublishers.ofByteArray(conteudo))
                        .build(),
                BodyHandlers.discarding());
        assertThat(put.statusCode()).isEqualTo(200);
        return JsonPath.read(corpo, "$.chave");
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult confirmar(String chave) {
        return adm.enviar("PUT", "/api/adm/cursos/" + curso + "/capa", "{\"chave\":\"%s\"}".formatted(chave));
    }

    private String chaveDaCapa() {
        return cursos.findById(curso).orElseThrow().getCapaChave();
    }

    @Test
    @DisplayName("envia, confirma (reduzida, JPEG sem EXIF), troca, aparece no catálogo e é removida")
    void cicloDaCapa() throws Exception {
        String original = enviar(ImagemFixture.pngTransparente(2000, 1500), "image/png");
        assertThat(confirmar(original))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.capaUrl")
                .asString()
                .startsWith(armazenamento.endpointPublico())
                .contains("/capas/" + curso + "/");
        String primeira = chaveDaCapa();
        assertThat(primeira).matches("capas/" + curso + "/[0-9a-f-]{36}\\.jpg");
        assertThat(storage.info(original)).as("original apagado").isEmpty();
        assertThat(storage.info(primeira))
                .get()
                .extracting(StorageService.InfoArquivo::contentType)
                .isEqualTo("image/jpeg");
        BufferedImage gravada = ImageIO.read(new ByteArrayInputStream(storage.baixar(primeira)));
        assertThat(gravada.getWidth()).isEqualTo(960);
        assertThat(gravada.getHeight()).isEqualTo(720);

        // Troca por um JPEG com EXIF: a capa anterior é apagada e o EXIF não chega ao bucket
        assertThat(confirmar(enviar(ImagemFixture.jpegComExif(400, 300, "GPS -23.5505"), "image/jpeg")))
                .hasStatusOk();
        String segunda = chaveDaCapa();
        assertThat(segunda).isNotEqualTo(primeira);
        assertThat(storage.info(primeira)).as("capa anterior apagada").isEmpty();
        assertThat(new String(storage.baixar(segunda), StandardCharsets.ISO_8859_1))
                .doesNotContain("Exif")
                .doesNotContain("GPS -23.5505");

        adm.publicar(curso);
        assertThat(mvc.get().uri("/api/cursos/" + slug))
                .bodyJson()
                .extractingPath("$.capaUrl")
                .isEqualTo("/api/cursos/" + slug + "/capa");
        var redirecionamento = mvc.get().uri("/api/cursos/" + slug + "/capa").exchange();
        assertThat(redirecionamento).hasStatus(HttpStatus.FOUND);
        assertThat(redirecionamento.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("max-age=300, public");
        String destino = redirecionamento.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(destino).startsWith(armazenamento.endpointPublico());
        var imagem = http.send(HttpRequest.newBuilder(URI.create(destino)).build(), BodyHandlers.ofByteArray());
        assertThat(imagem.statusCode()).isEqualTo(200);
        assertThat(imagem.body()).isEqualTo(storage.baixar(segunda));

        assertThat(adm.enviar("DELETE", "/api/adm/cursos/" + curso + "/capa", null))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(storage.info(segunda)).isEmpty();
        assertThat(mvc.get().uri("/api/cursos/" + slug + "/capa")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("conteúdo que não é imagem é recusado e apagado, mesmo enviado como image/png")
    void conteudoQueNaoEImagem() throws Exception {
        String chave = enviar("<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8), "image/png");

        assertThat(confirmar(chave))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Envie uma imagem JPEG ou PNG.");
        assertThat(storage.info(chave)).isEmpty();
        assertThat(chaveDaCapa()).isNull();
    }

    @Test
    @DisplayName("arquivo acima de 5 MB enviado mesmo assim é recusado na confirmação e apagado")
    void arquivoGrandeDemais() throws Exception {
        byte[] grande = new byte[5 * 1024 * 1024 + 1];
        var inicio = adm.enviar(
                "POST", "/api/adm/cursos/" + curso + "/capa/envio", "{\"contentType\":\"image/png\",\"tamanho\":10}");
        String url = JsonPath.read(CursoITHelper.corpo(inicio), "$.url");
        String chave = JsonPath.read(CursoITHelper.corpo(inicio), "$.chave");
        http.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("Content-Type", "image/png")
                        .PUT(BodyPublishers.ofByteArray(grande))
                        .build(),
                BodyHandlers.discarding());

        assertThat(confirmar(chave))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A imagem pode ter no máximo 5 MB.");
        assertThat(storage.info(chave)).isEmpty();
    }

    @Test
    @DisplayName("envio recusado: tipo não aceito, tamanho acima do limite, sem upload e chave de outro curso")
    void enviosRecusados() throws Exception {
        String rota = "/api/adm/cursos/" + curso + "/capa/envio";
        assertThat(adm.enviar("POST", rota, "{\"contentType\":\"image/svg+xml\",\"tamanho\":10}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Envie uma imagem JPEG ou PNG.");
        assertThat(adm.enviar("POST", rota, "{\"contentType\":\"image/png\",\"tamanho\":5242881}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A imagem pode ter no máximo 5 MB.");

        String naoEnviada = JsonPath.read(
                CursoITHelper.corpo(adm.enviar("POST", rota, "{\"contentType\":\"image/png\",\"tamanho\":10}")),
                "$.chave");
        assertThat(confirmar(naoEnviada))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A imagem não foi enviada. Comece um novo envio.");

        // Imagem válida enviada para outro curso não pode virar capa deste
        String outroSlug = stamp();
        long outro = adm.criarCurso("IT Outro " + outroSlug, outroSlug);
        long atual = curso;
        curso = outro;
        String chaveDoOutro = enviar(ImagemFixture.png(10, 10), "image/png");
        curso = atual;
        assertThat(confirmar(chaveDoOutro))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Envio de capa inválido. Comece um novo envio.");
        assertThat(storage.info(chaveDoOutro))
                .as("o envio do outro curso continua lá")
                .isPresent();
    }
}
