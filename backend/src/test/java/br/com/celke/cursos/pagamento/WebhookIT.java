package br.com.celke.cursos.pagamento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Cobre: recebimento das notificações — hottok certo grava (corpo mascarado) e enfileira; o mesmo id
 * é gravado uma vez; hottok errado ou ausente 401 sem gravar; endereço desconhecido 404; corpo que não
 * é JSON 200 gravado como erro; corpo grande demais 413; oferta não cadastrada e evento não tratado
 * ficam ignorados com o motivo.
 * Não cobre: efeito no pedido e na matrícula (PagamentoFluxoIT).
 * Dado criado: uma Hotmart inativa por teste e eventos com produto it-<stamp>.
 */
class WebhookIT extends IntegrationTest {

    private CursoITHelper adm;
    private PagamentoITHelper pagamentos;
    private PagamentoITHelper.Gateway gateway;

    @BeforeEach
    void configurar() {
        adm = new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA)));
        pagamentos = new PagamentoITHelper(mvc, adm);
        gateway = pagamentos.hotmart("hottok-" + stamp(), false);
    }

    /** Eventos do gateway desta classe com o id externo, pelo painel (mais recentes primeiro). */
    private List<Map<String, Object>> eventos(String idExterno) {
        return JsonPath.read(
                CursoITHelper.corpo(adm.enviar("GET", "/api/adm/pagamentos/eventos?size=100", null)),
                "$.itens[?(@.idExterno == '%s' && @.gatewayId == %d)]".formatted(idExterno, gateway.id()));
    }

    @Test
    @DisplayName("hottok certo: grava com o corpo mascarado, enfileira e o mesmo id não é gravado de novo")
    void gravaUmaVez() {
        String id = "evento-" + stamp();
        String produto = stamp();
        String corpo = pagamentos
                .notificacao("PURCHASE_APPROVED")
                .id(id)
                .produto(produto, "k2")
                .json();

        pagamentos.enviar(gateway, corpo, "recebido");
        pagamentos.enviar(gateway, corpo, "duplicado");

        List<Map<String, Object>> gravados = eventos(id);
        assertThat(gravados).singleElement().satisfies(evento -> {
            assertThat(evento).containsEntry("status", "RECEBIDO").containsEntry("tipoOriginal", "PURCHASE_APPROVED");
            assertThat(evento.get("transacao")).isEqualTo("HP17715690036014");
        });
        int eventoId = (Integer) gravados.getFirst().get("id");
        String detalhe = CursoITHelper.corpo(adm.enviar("GET", "/api/adm/pagamentos/eventos/" + eventoId, null));
        assertThat(detalhe).contains("***09").doesNotContain("12345678909", "5511999990000", "200.100.50.25");

        processarFila();

        assertThat(eventos(id))
                .singleElement()
                .satisfies(evento -> assertThat(evento)
                        .containsEntry("status", "IGNORADO")
                        .containsEntry("erro", "Oferta não cadastrada (produto %s, oferta k2).".formatted(produto)));
    }

    @Test
    @DisplayName("evento que não muda o pedido é gravado e ignorado")
    void naoTratado() {
        String id = "evento-" + stamp();
        pagamentos.enviar(
                gateway,
                pagamentos.notificacao("SUBSCRIPTION_CANCELLATION").id(id).json(),
                "recebido");

        processarFila();

        assertThat(eventos(id))
                .singleElement()
                .satisfies(evento -> assertThat(evento)
                        .containsEntry("status", "IGNORADO")
                        .containsEntry("erro", "O evento SUBSCRIPTION_CANCELLATION não muda o pedido."));
    }

    @Test
    @DisplayName("hottok errado ou ausente: 401 sem gravar nada")
    void hottokErrado() {
        String id = "evento-" + stamp();
        String corpo = pagamentos.notificacao("PURCHASE_APPROVED").id(id).json();
        var outroHottok = new PagamentoITHelper.Gateway(gateway.id(), gateway.webhookToken(), "hottok-errado");

        assertThat(pagamentos.webhook(outroHottok, corpo))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Notificação não autenticada.");
        assertThat(mvc.post()
                        .uri("/api/pagamentos/webhooks/" + gateway.webhookToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(eventos(id)).isEmpty();
    }

    @Test
    @DisplayName("endereço desconhecido: 404")
    void enderecoDesconhecido() {
        var desconhecido = new PagamentoITHelper.Gateway(0, "nao-existe-" + stamp(), gateway.hottok());

        assertThat(pagamentos.webhook(desconhecido, "{}"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Endereço de notificação desconhecido.");
        assertThat(pagamentos.webhook(new PagamentoITHelper.Gateway(0, "x".repeat(65), "h"), "{}"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("corpo que não é JSON: 200, gravado como erro (reenviar não resolve) e uma vez só")
    void corpoInvalido() {
        String corpo = "não é json " + stamp();

        pagamentos.enviar(gateway, corpo, "erro");
        pagamentos.enviar(gateway, corpo, "duplicado");

        List<Map<String, Object>> invalidos = JsonPath.read(
                CursoITHelper.corpo(adm.enviar("GET", "/api/adm/pagamentos/eventos?status=ERRO&size=100", null)),
                "$.itens[?(@.gatewayId == %d)]".formatted(gateway.id()));
        assertThat(invalidos)
                .singleElement()
                .satisfies(evento -> assertThat(evento)
                        .containsEntry("tipoOriginal", "INVALIDO")
                        .containsEntry("erro", "O corpo não é um JSON válido."));
        int id = (Integer) invalidos.getFirst().get("id");
        assertThat(adm.enviar("POST", "/api/adm/pagamentos/eventos/" + id + "/reprocessamento", null))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("corpo acima de 256 KB: 413")
    void grandeDemais() {
        String corpo = "{\"x\":\"" + "a".repeat(256 * 1024) + "\"}";

        assertThat(pagamentos.webhook(gateway, corpo))
                .hasStatus(HttpStatus.CONTENT_TOO_LARGE)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Notificação grande demais.");
    }
}
