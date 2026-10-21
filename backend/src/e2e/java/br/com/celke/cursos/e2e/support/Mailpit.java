package br.com.celke.cursos.e2e.support;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;

import io.restassured.path.json.JsonPath;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** E-mails entregues ao Mailpit da stack de e2e (enviados de verdade pelo worker). */
public final class Mailpit {

    private static final Pattern TOKEN_NO_LINK = Pattern.compile("[?&]token=([A-Za-z0-9_-]+)");

    private Mailpit() {}

    /** Espera o worker entregar um e-mail com o assunto para o destinatário e devolve o mais recente. */
    public static EmailRecebido aguardar(String destinatario, String assunto) {
        String id = await("e-mail \"" + assunto + "\" para " + destinatario)
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> idMaisRecente(destinatario, assunto), valor -> valor != null);
        JsonPath mensagem = given().baseUri(E2eConfig.MAILPIT)
                .get("/api/v1/message/{id}", id)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath();
        return new EmailRecebido(
                mensagem.getString("Subject"), mensagem.getString("From.Address"), mensagem.getString("HTML"));
    }

    /** Token do link do e-mail ({@code ...?token=<valor>}). */
    public static String token(EmailRecebido email) {
        Matcher matcher = TOKEN_NO_LINK.matcher(email.html());
        if (!matcher.find()) {
            throw new AssertionError("O e-mail não tem link com token: " + email.assunto());
        }
        return matcher.group(1);
    }

    private static String idMaisRecente(String destinatario, String assunto) {
        List<String> ids = given().baseUri(E2eConfig.MAILPIT)
                .queryParam("query", "to:\"%s\" subject:\"%s\"".formatted(destinatario, assunto))
                .get("/api/v1/search")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("messages.ID");
        return ids == null || ids.isEmpty() ? null : ids.getFirst();
    }

    public record EmailRecebido(String assunto, String remetente, String html) {}
}
