package br.com.celke.cursos.e2e.curso;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import br.com.celke.cursos.e2e.support.Adm;
import br.com.celke.cursos.e2e.support.Api;
import br.com.celke.cursos.e2e.support.Dados;
import br.com.celke.cursos.e2e.support.E2eConfig;
import br.com.celke.cursos.support.fixtures.ImagemFixture;
import io.restassured.config.EncoderConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Jornada: o ADM monta um curso (turma, módulo, aulas e capa enviada direto ao Garage pelo Caddy),
 * publica, e o visitante sem login encontra o curso no catálogo, com o conteúdo e a capa.
 */
class CatalogoE2E {

    private static final String HOST_MIDIA = "media.localhost";

    @Test
    @DisplayName("ADM cria e publica um curso com capa; o catálogo público mostra o curso")
    void admPublicaCatalogoMostra() {
        Adm adm = Adm.doSeed();
        String slug = Dados.stamp();

        // 1. Curso, turma ativa, módulo e duas aulas (uma gratuita)
        long curso = adm.criar(
                "/api/adm/cursos",
                "{\"titulo\":\"Curso %s\",\"slug\":\"%s\",\"resumo\":\"Jornada e2e\"}".formatted(slug, slug));
        long turma = adm.criar("/api/adm/cursos/" + curso + "/turmas", "{\"nome\":\"Turma e2e\"}");
        adm.alterarStatus("/api/adm/turmas/" + turma + "/status", "ATIVA");
        long modulo = adm.criar("/api/adm/turmas/" + turma + "/modulos", "{\"titulo\":\"Primeiros passos\"}");
        adm.criar("/api/adm/modulos/" + modulo + "/aulas", "{\"titulo\":\"Boas-vindas\",\"gratuita\":true}");
        adm.criar("/api/adm/modulos/" + modulo + "/aulas", "{\"titulo\":\"Instalação\"}");

        // 2. Capa: URL pré-assinada → PUT no Garage pelo Caddy (media) → confirmação na API
        byte[] png = ImagemFixture.png(1600, 900);
        Response envio = adm.requisicao()
                .body("{\"contentType\":\"image/png\",\"tamanho\":%d}".formatted(png.length))
                .post("/api/adm/cursos/" + curso + "/capa/envio")
                .then()
                .statusCode(200)
                .extract()
                .response();
        URI url = URI.create(envio.jsonPath().getString("url"));
        assertThat(url.getHost()).isEqualTo(HOST_MIDIA);
        midia().contentType("image/png")
                .body(png)
                .put(url.getRawPath() + "?" + url.getRawQuery())
                .then()
                .statusCode(200);
        adm.requisicao()
                .body("{\"chave\":\"%s\"}".formatted(envio.jsonPath().getString("chave")))
                .put("/api/adm/cursos/" + curso + "/capa")
                .then()
                .statusCode(200)
                .body("capaUrl", startsWith(E2eConfig.origem(HOST_MIDIA)));

        // 3. Antes de publicar, o catálogo não conhece o curso
        Api.aluno().get("/api/cursos/" + slug).then().statusCode(404);
        adm.alterarStatus("/api/adm/cursos/" + curso + "/status", "PUBLICADO");

        // 4. Visitante sem login: listagem, detalhe e capa
        assertThat(slugsNoCatalogo()).contains(slug);
        Api.aluno()
                .get("/api/cursos/" + slug)
                .then()
                .statusCode(200)
                .body("titulo", equalTo("Curso " + slug))
                .body("capaUrl", equalTo("/api/cursos/" + slug + "/capa"))
                .body("modulos[0].titulo", equalTo("Primeiros passos"))
                .body("modulos[0].aulas.titulo", equalTo(List.of("Boas-vindas", "Instalação")))
                .body("modulos[0].aulas.gratuita", equalTo(List.of(true, false)));

        Response redirecionamento = Api.aluno()
                .redirects()
                .follow(false)
                .get("/api/cursos/" + slug + "/capa")
                .then()
                .statusCode(302)
                .extract()
                .response();
        URI capa = URI.create(redirecionamento.getHeader("Location"));
        assertThat(capa.getHost()).isEqualTo(HOST_MIDIA);
        Response imagem = midia().get(capa.getRawPath() + "?" + capa.getRawQuery())
                .then()
                .statusCode(200)
                .extract()
                .response();
        assertThat(imagem.getContentType()).isEqualTo("image/jpeg");
        assertThat(imagem.asByteArray()).startsWith((byte) 0xFF, (byte) 0xD8);
    }

    @Test
    @DisplayName("aluno não acessa a gestão de cursos pelo Caddy")
    void alunoNaoGerencia() {
        String token =
                Api.login(E2eConfig.ALUNO_SEED, E2eConfig.SENHA_SEED).jsonPath().getString("accessToken");

        Api.adm()
                .header("Authorization", Api.bearer(token))
                .get("/api/adm/cursos")
                .then()
                .statusCode(403)
                .body("detail", equalTo("Acesso negado."));
    }

    /**
     * Requisição ao Garage pelo Caddy com a URL pré-assinada como veio: sem recodificar a query e sem
     * acrescentar charset ao Content-Type, que fazem parte da assinatura.
     */
    private static RequestSpecification midia() {
        return given().config(RestAssuredConfig.config()
                        .encoderConfig(EncoderConfig.encoderConfig()
                                .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                .urlEncodingEnabled(false)
                .baseUri("http://localhost")
                .port(E2eConfig.PORTA)
                .header("Host", HOST_MIDIA + ":" + E2eConfig.PORTA);
    }

    private static List<String> slugsNoCatalogo() {
        List<String> slugs = new ArrayList<>();
        for (int pagina = 0; ; pagina++) {
            var corpo = Api.aluno()
                    .get("/api/cursos?size=100&page=" + pagina)
                    .then()
                    .statusCode(200)
                    .extract()
                    .jsonPath();
            slugs.addAll(corpo.getList("itens.slug", String.class));
            if (pagina + 1 >= corpo.getInt("totalPaginas")) {
                return slugs;
            }
        }
    }
}
