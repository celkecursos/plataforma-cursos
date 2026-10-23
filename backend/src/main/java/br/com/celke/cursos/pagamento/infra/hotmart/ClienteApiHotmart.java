package br.com.celke.cursos.pagamento.infra.hotmart;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * API REST da Hotmart: token OAuth por client credentials (guardado até 5 minutos antes de vencer) e
 * histórico de vendas filtrado pela transação (reconciliação).
 */
public class ClienteApiHotmart {

    static final Duration MARGEM_TOKEN = Duration.ofMinutes(5);

    private final RestClient http;
    private final HotmartProperties props;
    private final Clock clock;
    private final Map<String, TokenGuardado> tokens = new ConcurrentHashMap<>();

    public ClienteApiHotmart(RestClient http, HotmartProperties props, Clock clock) {
        this.http = http;
        this.props = props;
        this.clock = clock;
    }

    /** Cliente HTTP com o tempo máximo de conexão e de resposta de {@code props.timeout()}. */
    public static RestClient restClient(RestClient.Builder builder, HotmartProperties props) {
        HttpClient cliente =
                HttpClient.newBuilder().connectTimeout(props.timeout()).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(props.timeout());
        return builder.requestFactory(fabrica).build();
    }

    /** Token de acesso; pede um novo quando o guardado está a menos de 5 minutos de vencer. */
    public String token(Credenciais credenciais) {
        Instant agora = clock.instant();
        TokenGuardado guardado = tokens.get(credenciais.chaveCache());
        if (guardado != null && guardado.expiraEm().minus(MARGEM_TOKEN).isAfter(agora)) {
            return guardado.token();
        }
        JsonNode resposta = chamar(() -> http.post()
                .uri(
                        props.urlToken() + "?grant_type=client_credentials&client_id={id}&client_secret={segredo}",
                        credenciais.clientId(),
                        credenciais.clientSecret())
                .header(HttpHeaders.AUTHORIZATION, "Basic " + credenciais.basic())
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class));
        String token = InterpretadorHotmart.texto(resposta.path("access_token"));
        if (token == null) {
            throw new FalhaApiHotmart("A Hotmart não devolveu o token de acesso.");
        }
        long segundos = resposta.path("expires_in").asLong(3600);
        tokens.put(credenciais.chaveCache(), new TokenGuardado(token, agora.plusSeconds(segundos)));
        return token;
    }

    /**
     * A venda da transação no histórico de vendas. Sem filtro de status a Hotmart só devolve as
     * aprovadas e completas; {@code status} nulo pede essas.
     */
    public Optional<JsonNode> venda(Credenciais credenciais, String transacao, String status) {
        String token = token(credenciais);
        String base = credenciais.sandbox() ? props.urlApiSandbox() : props.urlApi();
        JsonNode resposta = chamar(() -> http.get()
                .uri(
                        base + "/sales/history?transaction={transacao}"
                                + (status == null ? "" : "&transaction_status={status}"),
                        status == null ? new Object[] {transacao} : new Object[] {transacao, status})
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class));
        JsonNode itens = resposta.path("items");
        return itens.isArray() && !itens.isEmpty() ? Optional.of(itens.get(0)) : Optional.empty();
    }

    private static JsonNode chamar(Supplier<JsonNode> chamada) {
        try {
            JsonNode resposta = chamada.get();
            if (resposta == null) {
                throw new FalhaApiHotmart("A Hotmart respondeu sem corpo.");
            }
            return resposta;
        } catch (RestClientResponseException e) {
            throw new FalhaApiHotmart(
                    "A Hotmart recusou a chamada (HTTP %d)."
                            .formatted(e.getStatusCode().value()),
                    e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new FalhaApiHotmart("Não foi possível falar com a Hotmart: " + e.getMessage());
        }
    }

    /** Credenciais da API (Ferramentas → Credenciais na Hotmart). */
    public record Credenciais(String clientId, String clientSecret, String basic, boolean sandbox) {

        public Credenciais {
            if (basic != null && basic.regionMatches(true, 0, "Basic ", 0, 6)) {
                basic = basic.substring(6).strip();
            }
        }

        String chaveCache() {
            return clientId + (sandbox ? "@sandbox" : "@producao");
        }
    }

    private record TokenGuardado(String token, Instant expiraEm) {}

    /** Falha ao chamar a API (credencial recusada, rede, resposta inesperada). */
    public static class FalhaApiHotmart extends RuntimeException {

        private final int status;

        public FalhaApiHotmart(String mensagem) {
            this(mensagem, 0);
        }

        public FalhaApiHotmart(String mensagem, int status) {
            super(mensagem);
            this.status = status;
        }

        /** Status HTTP da recusa; 0 quando não houve resposta. */
        public int getStatus() {
            return status;
        }
    }
}
