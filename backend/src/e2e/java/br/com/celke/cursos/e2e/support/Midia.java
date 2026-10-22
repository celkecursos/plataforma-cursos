package br.com.celke.cursos.e2e.support;

import static io.restassured.RestAssured.given;

import io.restassured.config.EncoderConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.specification.RequestSpecification;
import java.net.URI;

/** Acesso ao Garage pelo Caddy ({@code media.localhost}), com as URLs pré-assinadas devolvidas pela API. */
public final class Midia {

    public static final String HOST = "media.localhost";

    private Midia() {}

    /**
     * Requisição com a URL pré-assinada como veio: sem recodificar a query e sem acrescentar charset ao
     * Content-Type, que fazem parte da assinatura.
     */
    public static RequestSpecification requisicao() {
        return given().config(RestAssuredConfig.config()
                        .encoderConfig(EncoderConfig.encoderConfig()
                                .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                .urlEncodingEnabled(false)
                .baseUri("http://localhost")
                .port(E2eConfig.PORTA)
                .header("Host", HOST + ":" + E2eConfig.PORTA);
    }

    /** Caminho e query da URL pré-assinada, para usar com {@link #requisicao()}. */
    public static String caminho(URI url) {
        return url.getRawPath() + "?" + url.getRawQuery();
    }
}
