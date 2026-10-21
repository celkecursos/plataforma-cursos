package br.com.celke.cursos.support;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import org.springframework.web.client.RestClient;

/** Consulta os e-mails capturados pelo Mailpit (API HTTP do container). */
public class Mailpit {

    private final RestClient http;

    public Mailpit(String urlBase) {
        this.http = RestClient.create(urlBase);
    }

    /** Espera chegar um e-mail para o destinatário e devolve o mais recente. */
    public EmailRecebido aguardarEmailPara(String destinatario) {
        String id = await("e-mail para " + destinatario)
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> idMaisRecente(destinatario), resultado -> resultado != null);
        return http.get().uri("/api/v1/message/{id}", id).retrieve().body(EmailRecebido.class);
    }

    private String idMaisRecente(String destinatario) {
        Busca busca = http.get()
                .uri(u -> u.path("/api/v1/search")
                        .queryParam("query", "to:\"" + destinatario + "\"")
                        .build())
                .retrieve()
                .body(Busca.class);
        return busca == null || busca.messages().isEmpty()
                ? null
                : busca.messages().getFirst().ID();
    }

    record Busca(List<Resumo> messages) {}

    record Resumo(String ID) {}

    public record Endereco(String Name, String Address) {}

    /** Campos do e-mail na API do Mailpit (nomes em PascalCase, como a API devolve). */
    public record EmailRecebido(String Subject, Endereco From, List<Endereco> To, String HTML, String Text) {}
}
