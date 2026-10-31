package br.com.celke.cursos.pagamento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.matricula.MatriculaITHelper;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Cobre: configuração dos meios de pagamento pelo painel (tipos, criação, segredos que nunca voltam
 * nem ficam em texto puro no banco, segredo em branco mantido, validação, teste de credenciais e um
 * ativo por vez) e as ofertas da turma (criação, duplicada, alteração, status, catálogo).
 * Não cobre: notificações (WebhookIT) e compra (PagamentoFluxoIT).
 * Dado criado: gateways "Hotmart IT", curso it-<stamp> publicado e ofertas com produto it-<stamp>.
 */
class GatewayAdmIT extends IntegrationTest {

    private CursoITHelper adm;
    private PagamentoITHelper pagamentos;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void login() {
        adm = new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA)));
        pagamentos = new PagamentoITHelper(mvc, adm);
    }

    @Test
    @DisplayName("tipos disponíveis com os campos de configuração; a cifra está disponível")
    void tipos() {
        assertThat(adm.enviar("GET", "/api/adm/pagamentos/tipos", null))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"cifraDisponivel":true,"tipos":[{"tipo":"HOTMART","nome":"Hotmart",
                         "campos":[{"chave":"hottok","secreto":true,"obrigatorio":true},
                                   {"chave":"ambiente","tipo":"SELECAO","padrao":"PRODUCAO","opcoes":["PRODUCAO","SANDBOX"]},
                                   {"chave":"urlCheckoutBase","tipo":"URL","padrao":"https://pay.hotmart.com"},
                                   {"chave":"clientId","secreto":false},{"chave":"clientSecret","secreto":true},
                                   {"chave":"basic","secreto":true}]}]}
                        """);
    }

    @Test
    @DisplayName("segredos nunca voltam nem ficam em texto puro; em branco no PUT, o atual continua valendo")
    void segredos() {
        String hottok = "hottok-secreto-" + stamp();
        var criado = adm.enviar(
                "POST",
                "/api/adm/pagamentos/gateways",
                "{\"tipo\":\"HOTMART\",\"nome\":\" Hotmart IT \",\"valores\":{\"hottok\":\"%s\",\"clientId\":\"cid\"}}"
                        .formatted(hottok));
        long id = CursoITHelper.idCriado(criado);
        String corpo = CursoITHelper.corpo(criado);
        assertThat(corpo).doesNotContain(hottok);
        assertThat(criado).bodyJson().isLenientlyEqualTo("""
                        {"tipo":"HOTMART","nomeTipo":"Hotmart","nome":"Hotmart IT","ativo":false,
                         "valores":{"ambiente":"PRODUCAO","urlCheckoutBase":"https://pay.hotmart.com","clientId":"cid"},
                         "segredos":{"hottok":{"configurado":true,"final4":"%s"},
                                     "clientSecret":{"configurado":false,"final4":null}},
                         "segredosLegiveis":true}
                        """.formatted(hottok.substring(hottok.length() - 4)));
        String webhookUrl = JsonPath.read(corpo, "$.webhookUrl");
        assertThat(webhookUrl).startsWith("http://localhost:5173/api/pagamentos/webhooks/");
        String token = webhookUrl.substring(webhookUrl.lastIndexOf('/') + 1);

        String noBanco = jdbc.sql(
                        "SELECT configuracao::text || encode(segredos, 'escape') FROM gateways_pagamento WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .single();
        assertThat(noBanco).contains("cid").doesNotContain(hottok);
        assertThat(CursoITHelper.corpo(adm.enviar("GET", "/api/adm/pagamentos/gateways", null)))
                .contains("\"id\":" + id)
                .doesNotContain(hottok);

        assertThat(adm.enviar(
                        "PUT",
                        "/api/adm/pagamentos/gateways/" + id,
                        "{\"nome\":\"Renomeada\",\"valores\":{\"hottok\":\"\",\"ambiente\":\"SANDBOX\"}}"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"nome\":\"Renomeada\",\"valores\":{\"ambiente\":\"SANDBOX\"},"
                        + "\"segredos\":{\"hottok\":{\"configurado\":true}}}");
        var gateway = new PagamentoITHelper.Gateway(id, token, hottok);
        pagamentos.enviar(
                gateway, pagamentos.notificacao("SUBSCRIPTION_CANCELLATION").json(), "recebido");

        assertThat(adm.enviar(
                        "PUT",
                        "/api/adm/pagamentos/gateways/" + id,
                        "{\"nome\":\"Renomeada\",\"valores\":{\"hottok\":\"novo-hottok\"}}"))
                .hasStatusOk();
        assertThat(pagamentos.webhook(gateway, "{}")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("campos inválidos voltam na lista de erros; meio inexistente 404")
    void validacao() {
        assertThat(adm.enviar(
                        "POST",
                        "/api/adm/pagamentos/gateways",
                        "{\"tipo\":\"HOTMART\",\"nome\":\"x\",\"valores\":{\"ambiente\":\"OUTRO\"}}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .isEqualTo(List.of("valores.ambiente", "valores.hottok"));
        assertThat(adm.enviar("POST", "/api/adm/pagamentos/gateways", "{\"nome\":\"\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .isEqualTo(List.of("nome", "tipo"));
        assertThat(adm.enviar("GET", "/api/adm/pagamentos/gateways/999999999", null))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Meio de pagamento não encontrado.");
    }

    @Test
    @DisplayName("testar: só o hottok passa; credenciais da API que a Hotmart não confirma falham")
    void testar() {
        var gateway = pagamentos.hotmart("hottok-" + stamp(), false);
        assertThat(adm.enviar("POST", "/api/adm/pagamentos/gateways/" + gateway.id() + "/teste", null))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.ok")
                .isEqualTo(true);

        adm.enviar(
                "PUT",
                "/api/adm/pagamentos/gateways/" + gateway.id(),
                "{\"nome\":\"Hotmart IT\",\"valores\":{\"clientId\":\"id\",\"clientSecret\":\"s\",\"basic\":\"b\"}}");
        var teste = adm.enviar("POST", "/api/adm/pagamentos/gateways/" + gateway.id() + "/teste", null);
        assertThat(teste).hasStatusOk().bodyJson().extractingPath("$.ok").isEqualTo(false);
        assertThat(CursoITHelper.corpo(teste)).contains("Não foi possível falar com a Hotmart");
    }

    @Test
    @DisplayName("um ativo por vez: ativar outro desativa o anterior; desativar tira o ativo")
    void umAtivo() {
        var primeiro = pagamentos.hotmart("h1-" + stamp(), true);
        var segundo = pagamentos.hotmart("h2-" + stamp(), true);

        assertThat(adm.enviar("GET", "/api/adm/pagamentos/gateways/" + primeiro.id(), null))
                .bodyJson()
                .extractingPath("$.ativo")
                .isEqualTo(false);
        List<Integer> ativos = JsonPath.read(
                CursoITHelper.corpo(adm.enviar("GET", "/api/adm/pagamentos/gateways", null)),
                "$[?(@.ativo == true)].id");
        assertThat(ativos).containsExactly((int) segundo.id());

        assertThat(adm.enviar("DELETE", "/api/adm/pagamentos/gateways/" + segundo.id() + "/ativacao", null))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.ativo")
                .isEqualTo(false);
    }

    @Test
    @DisplayName("ofertas: cria no gateway ativo, recusa duplicada, altera, desativa e o catálogo acompanha")
    void ofertas() {
        String slug = stamp();
        long turma = new MatriculaITHelper(adm).cursoPublicado(slug).turma();
        var gateway = pagamentos.hotmart("h-" + stamp(), true);
        String produto = stamp();
        String corpo =
                "{\"produtoExterno\":\"%s\",\"ofertaExterna\":\"k2\",\"codigoCheckout\":\"X1\",\"titulo\":\"À vista\","
                                .formatted(produto)
                        + "\"precoCentavos\":19700,\"moeda\":\"brl\",\"diasAcesso\":365}";

        var criada = adm.enviar("POST", "/api/adm/turmas/" + turma + "/ofertas", corpo);
        long oferta = CursoITHelper.idCriado(criada);
        assertThat(criada).bodyJson().isLenientlyEqualTo("""
                        {"turmaId":%d,"gatewayId":%d,"gatewayNome":"Hotmart IT","gatewayAtivo":true,
                         "produtoExterno":"%s","ofertaExterna":"k2","codigoCheckout":"X1","moeda":"BRL",
                         "diasAcesso":365,"ativa":true}
                        """.formatted(turma, gateway.id(), produto));
        assertThat(adm.enviar("POST", "/api/adm/turmas/" + turma + "/ofertas", corpo))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Já existe uma oferta com este produto e esta oferta no meio de pagamento.");
        assertThat(adm.enviar("GET", "/api/cursos/" + slug, null))
                .bodyJson()
                .isLenientlyEqualTo(
                        "{\"ofertas\":[{\"id\":%d,\"titulo\":\"À vista\",\"precoCentavos\":19700,\"moeda\":\"BRL\"}]}"
                                .formatted(oferta));

        assertThat(adm.enviar("PUT", "/api/adm/ofertas/" + oferta, corpo.replace("À vista", "Parcelado")))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.titulo")
                .isEqualTo("Parcelado");
        assertThat(adm.enviar("PATCH", "/api/adm/ofertas/" + oferta + "/status", "{\"ativa\":false}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.ativa")
                .isEqualTo(false);
        assertThat(adm.enviar("GET", "/api/adm/turmas/" + turma + "/ofertas", null))
                .bodyJson()
                .extractingPath("$[0].titulo")
                .isEqualTo("Parcelado");
        assertThat(adm.enviar("GET", "/api/cursos/" + slug, null))
                .bodyJson()
                .extractingPath("$.ofertas")
                .isEqualTo(List.of());
    }

    @Test
    @DisplayName("oferta em turma inexistente 404; validação 400; oferta inexistente 404")
    void ofertasErros() {
        String corpo =
                "{\"produtoExterno\":\"p\",\"ofertaExterna\":\"o\",\"titulo\":\"t\",\"precoCentavos\":1,\"moeda\":\"BRL\"}";
        assertThat(adm.enviar("POST", "/api/adm/turmas/999999999/ofertas", corpo))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Turma não encontrada.");
        assertThat(adm.enviar("POST", "/api/adm/turmas/1/ofertas", "{\"moeda\":\"R$\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .isEqualTo(List.of("moeda", "ofertaExterna", "precoCentavos", "produtoExterno", "titulo"));
        assertThat(adm.enviar("PATCH", "/api/adm/ofertas/999999999/status", "{\"ativa\":true}"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }
}
