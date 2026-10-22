package br.com.celke.cursos.pagamento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.pagamento.infra.hotmart.FixturesHotmart;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Configura a Hotmart e ofertas pela API do ADM e manda notificações montadas a partir das fixtures. */
public final class PagamentoITHelper {

    private final MockMvcTester mvc;
    private final CursoITHelper adm;
    private final JsonMapper json = JsonMapper.builder().build();

    public PagamentoITHelper(MockMvcTester mvc, CursoITHelper adm) {
        this.mvc = mvc;
        this.adm = adm;
    }

    /** Hotmart configurada só com o hottok; ativa se pedido. */
    public Gateway hotmart(String hottok, boolean ativar) {
        MvcTestResult criado = adm.enviar(
                "POST",
                "/api/adm/pagamentos/gateways",
                "{\"tipo\":\"HOTMART\",\"nome\":\"Hotmart IT\",\"valores\":{\"hottok\":\"%s\"}}".formatted(hottok));
        long id = CursoITHelper.idCriado(criado);
        String webhookUrl = JsonPath.read(CursoITHelper.corpo(criado), "$.webhookUrl");
        if (ativar) {
            assertThat(adm.enviar("POST", "/api/adm/pagamentos/gateways/" + id + "/ativacao", null))
                    .hasStatusOk();
        }
        return new Gateway(id, webhookUrl.substring(webhookUrl.lastIndexOf('/') + 1), hottok);
    }

    /** Oferta na turma, no gateway ativo; devolve o id. */
    public long oferta(long turma, String produto, String oferta, Integer diasAcesso) {
        return CursoITHelper.idCriado(adm.enviar(
                "POST",
                "/api/adm/turmas/" + turma + "/ofertas",
                "{\"produtoExterno\":\"%s\",\"ofertaExterna\":\"%s\",\"titulo\":\"À vista\",\"precoCentavos\":19700,\"moeda\":\"BRL\"%s}"
                        .formatted(produto, oferta, diasAcesso == null ? "" : ",\"diasAcesso\":" + diasAcesso)));
    }

    /** Checkout do aluno; confere o 201 e devolve o código do pedido. */
    public String checkout(String bearer, long oferta) {
        MvcTestResult resposta = mvc.post()
                .uri("/api/pagamentos/checkout")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ofertaId\":%d}".formatted(oferta))
                .exchange();
        assertThat(resposta).hasStatus(HttpStatus.CREATED);
        return JsonPath.read(CursoITHelper.corpo(resposta), "$.pedido");
    }

    public MvcTestResult webhook(Gateway gateway, String corpo) {
        return mvc.post()
                .uri("/api/pagamentos/webhooks/" + gateway.webhookToken())
                .header("X-HOTMART-HOTTOK", gateway.hottok())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo)
                .exchange();
    }

    /** Envia e confere o 200 com o resultado esperado. */
    public void enviar(Gateway gateway, String corpo, String resultado) {
        assertThat(webhook(gateway, corpo))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.resultado")
                .isEqualTo(resultado);
    }

    /** Notificação a partir da fixture do evento, com id novo. */
    public Notificacao notificacao(String evento) {
        return new Notificacao((ObjectNode) json.readTree(FixturesHotmart.corpo(evento)))
                .id(UUID.randomUUID().toString());
    }

    public record Gateway(long id, String webhookToken, String hottok) {}

    /** Corpo de uma notificação com os campos que os testes trocam. */
    public final class Notificacao {

        private final ObjectNode raiz;

        private Notificacao(ObjectNode raiz) {
            this.raiz = raiz;
        }

        public Notificacao id(String id) {
            raiz.put("id", id);
            return this;
        }

        public Notificacao ocorridoEm(long epochMillis) {
            raiz.put("creation_date", epochMillis);
            return this;
        }

        public Notificacao xcod(String xcod) {
            ObjectNode origem = (ObjectNode) raiz.get("data").get("purchase").get("origin");
            if (xcod == null) {
                origem.remove("xcod");
            } else {
                origem.put("xcod", xcod);
            }
            return this;
        }

        public Notificacao transacao(String transacao) {
            ((ObjectNode) raiz.get("data").get("purchase")).put("transaction", transacao);
            return this;
        }

        public Notificacao produto(String produto, String oferta) {
            ((ObjectNode) raiz.get("data").get("product")).put("id", produto);
            ((ObjectNode) raiz.get("data").get("purchase").get("offer")).put("code", oferta);
            return this;
        }

        public Notificacao comprador(String email, String nome) {
            ObjectNode comprador = (ObjectNode) raiz.get("data").get("buyer");
            comprador.put("email", email);
            comprador.put("name", nome);
            return this;
        }

        public String json() {
            return json.writeValueAsString(raiz);
        }
    }
}
