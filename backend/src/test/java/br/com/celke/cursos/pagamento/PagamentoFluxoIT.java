package br.com.celke.cursos.pagamento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.matricula.MatriculaITHelper;
import br.com.celke.cursos.pagamento.application.ProcessamentoEventoService;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.Mailpit;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: a compra de ponta a ponta na API — checkout com o xcod, notificação aprovada que matricula e
 * avisa, reembolso e chargeback que tiram o acesso, aprovação depois do reembolso que não reabre,
 * evento antigo que não desfaz o mais novo, boleto aguardando, venda sem xcod que cria a conta sem
 * senha (com o link de criar senha), dois eventos do mesmo pedido ao mesmo tempo e o reprocessamento
 * depois de cadastrar a oferta. A página de retorno vê só o status sem login e o curso com o dono.
 * Não cobre: recebimento e autenticação do webhook (WebhookIT), painel (GatewayAdmIT).
 * Dado criado: curso it-<stamp> publicado, Hotmart ativa da classe, ofertas e contas it-<stamp>.
 */
class PagamentoFluxoIT extends IntegrationTest {

    @Autowired
    private Mailpit mailpit;

    @Autowired
    private ProcessamentoEventoService processamento;

    private CursoITHelper adm;
    private PagamentoITHelper pagamentos;
    private PagamentoITHelper.Gateway gateway;
    private long turma;
    private String produto;
    private long oferta;

    @BeforeEach
    void configurar() {
        adm = new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA)));
        pagamentos = new PagamentoITHelper(mvc, adm);
        turma = new MatriculaITHelper(adm).cursoPublicado(stamp()).turma();
        gateway = pagamentos.hotmart("hottok-" + stamp(), true);
        produto = stamp();
        oferta = pagamentos.oferta(turma, produto, "k2", 365);
    }

    private PagamentoITHelper.Notificacao evento(String tipo, String xcod, String transacao) {
        return pagamentos.notificacao(tipo).xcod(xcod).transacao(transacao).produto(produto, "k2");
    }

    private void notificar(PagamentoITHelper.Notificacao notificacao) {
        pagamentos.enviar(gateway, notificacao.json(), "recebido");
        processarFila();
    }

    private MvcTestResult turmaDoAluno(String token) {
        return mvc.get()
                .uri("/api/aluno/turmas/" + turma)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange();
    }

    private MvcTestResult situacao(String codigo, String token) {
        var requisicao = mvc.get().uri("/api/pagamentos/pedidos/" + codigo);
        if (token != null) {
            requisicao = requisicao.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return requisicao.exchange();
    }

    /** Pedido no painel pelo código (lista filtrada pelo e-mail do comprador). */
    private Map<String, Object> pedidoNoPainel(String email) {
        List<Map<String, Object>> itens = JsonPath.read(
                CursoITHelper.corpo(adm.enviar("GET", "/api/adm/pagamentos/pedidos?email=" + email, null)), "$.itens");
        assertThat(itens).hasSize(1);
        return itens.getFirst();
    }

    @Test
    @DisplayName("checkout → aprovado matricula e avisa → reembolso tira o acesso → aprovado de novo não reabre")
    void compraEReembolso() {
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);
        String token = login(aluno.email(), aluno.senha());

        // 1. Checkout: URL da Hotmart com a oferta, o e-mail e o código do pedido
        MvcTestResult checkout = mvc.post()
                .uri("/api/pagamentos/checkout")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ofertaId\":%d}".formatted(oferta))
                .exchange();
        assertThat(checkout).hasStatus(HttpStatus.CREATED);
        String codigo = JsonPath.read(CursoITHelper.corpo(checkout), "$.pedido");
        assertThat(codigo).hasSize(22);
        assertThat(checkout).bodyJson().isLenientlyEqualTo("""
                        {"tipo":"REDIRECIONAMENTO","url":"https://pay.hotmart.com/%s?off=k2&email=%s&name=Usu%%C3%%A1rio+%s&xcod=%s&sck=plataforma"}
                        """.formatted(
                produto, aluno.email().replace("@", "%40"), aluno.email().replace("@", "%40"), codigo));
        assertThat(situacao(codigo, null)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"codigo":"%s","status":"CRIADO","turmaId":null,"cursoTitulo":null}
                """.formatted(codigo));
        assertThat(turmaDoAluno(token)).hasStatus(HttpStatus.FORBIDDEN);

        // 2. Aprovado: matrícula e e-mail de confirmação (conta com senha: sem link de criar senha)
        Instant aprovado = Instant.now();
        notificar(evento("PURCHASE_APPROVED", codigo, "HP-" + codigo).ocorridoEm(aprovado.toEpochMilli()));
        assertThat(turmaDoAluno(token)).hasStatusOk();
        assertThat(situacao(codigo, token)).bodyJson().isLenientlyEqualTo("""
                {"status":"APROVADO","turmaId":%d,"turmaNome":"Turma"}
                """.formatted(turma));
        assertThat(situacao(codigo, null))
                .bodyJson()
                .extractingPath("$.turmaId")
                .isNull();
        Mailpit.EmailRecebido email = mailpit.aguardarEmailPara(aluno.email());
        assertThat(email.Subject()).isEqualTo("Sua matrícula foi confirmada");
        assertThat(email.HTML())
                .contains("http://localhost:5173/turmas/" + turma)
                .doesNotContain("token=");

        // 3. Reembolso: acesso termina
        notificar(evento("PURCHASE_REFUNDED", codigo, "HP-" + codigo)
                .ocorridoEm(aprovado.plusSeconds(60).toEpochMilli()));
        assertThat(turmaDoAluno(token)).hasStatus(HttpStatus.FORBIDDEN);

        // 4. Aprovado de novo (reenvio tardio): não reabre
        notificar(evento("PURCHASE_APPROVED", codigo, "HP-" + codigo)
                .ocorridoEm(aprovado.plusSeconds(120).toEpochMilli()));
        assertThat(turmaDoAluno(token)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(situacao(codigo, token))
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("REEMBOLSADO");
        assertThat(mailpit.quantidadePara(aluno.email())).isEqualTo(1);

        // 5. Painel: linha do tempo e eventos
        Map<String, Object> pedido = pedidoNoPainel(aluno.email());
        assertThat(pedido).containsEntry("origem", "CHECKOUT").containsEntry("formaPagamento", "CARTAO");
        String detalhe =
                CursoITHelper.corpo(adm.enviar("GET", "/api/adm/pagamentos/pedidos/" + pedido.get("id"), null));
        assertThat(JsonPath.<List<String>>read(detalhe, "$.historico[*].para"))
                .containsExactly("CRIADO", "APROVADO", "REEMBOLSADO");
        assertThat(JsonPath.<List<String>>read(detalhe, "$.eventos[*].status"))
                .containsExactly("PROCESSADO", "PROCESSADO", "IGNORADO");
    }

    @Test
    @DisplayName("venda sem xcod: cria a conta sem senha, matricula e manda o link de criar senha")
    void vendaExterna() {
        String email = stamp() + "@teste.celke.uno";

        notificar(evento("PURCHASE_APPROVED", null, "HP-" + stamp()).comprador(email, "Compradora Externa"));

        Map<String, Object> pedido = pedidoNoPainel(email);
        assertThat(pedido).containsEntry("origem", "EXTERNO").containsEntry("status", "APROVADO");
        assertThat(pedido.get("matriculaId")).isNotNull();
        Mailpit.EmailRecebido recebido = mailpit.aguardarEmailPara(email);
        assertThat(recebido.Subject()).isEqualTo("Sua matrícula foi confirmada");
        assertThat(recebido.HTML())
                .contains("/redefinir-senha?token=")
                .contains("origem=pagamento")
                .contains("Criar minha senha");
        assertThat(mvc.post()
                        .uri("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"qualquer-senha-1\"}".formatted(email)))
                .hasStatus(HttpStatus.UNAUTHORIZED);

        assertThat(mvc.post()
                        .uri("/api/auth/redefinir-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"senha\":\"senha-criada-agora\"}"
                                .formatted(Mailpit.tokenDoLink(recebido))))
                .hasStatusOk();
        String token = login(email, "senha-criada-agora");
        assertThat(turmaDoAluno(token)).hasStatusOk();
    }

    @Test
    @DisplayName("chargeback tira o acesso; boleto impresso só aguarda; evento antigo não desfaz o novo")
    void chargebackBoletoForaDeOrdem() {
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);
        String token = login(aluno.email(), aluno.senha());
        String codigo = pagamentos.checkout(bearer(token), oferta);
        String transacao = "HP-" + codigo;
        Instant t = Instant.now();

        notificar(evento("PURCHASE_BILLET_PRINTED", codigo, transacao).ocorridoEm(t.toEpochMilli()));
        assertThat(situacao(codigo, token))
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("AGUARDANDO_PAGAMENTO");
        assertThat(turmaDoAluno(token)).hasStatus(HttpStatus.FORBIDDEN);

        notificar(evento("PURCHASE_APPROVED", codigo, transacao)
                .ocorridoEm(t.plusSeconds(60).toEpochMilli()));
        // Cancelamento com data anterior à aprovação chega depois: não desfaz
        notificar(evento("PURCHASE_CANCELED", codigo, transacao)
                .ocorridoEm(t.plusSeconds(30).toEpochMilli()));
        assertThat(turmaDoAluno(token)).hasStatusOk();
        assertThat(situacao(codigo, token))
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("APROVADO");

        notificar(evento("PURCHASE_CHARGEBACK", codigo, transacao)
                .ocorridoEm(t.plusSeconds(90).toEpochMilli()));
        assertThat(turmaDoAluno(token)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(situacao(codigo, token))
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("CHARGEBACK");
    }

    @Test
    @DisplayName("dois eventos do mesmo pedido ao mesmo tempo: a trava aplica um, o outro é ignorado")
    void concorrencia() throws Exception {
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);
        String codigo = pagamentos.checkout(bearer(login(aluno.email(), aluno.senha())), oferta);
        String primeiro = "evento-" + stamp();
        String segundo = "evento-" + stamp();
        pagamentos.enviar(
                gateway,
                evento("PURCHASE_APPROVED", codigo, "HP-" + codigo).id(primeiro).json(),
                "recebido");
        pagamentos.enviar(
                gateway,
                evento("PURCHASE_APPROVED", codigo, "HP-" + codigo).id(segundo).json(),
                "recebido");
        String lista =
                CursoITHelper.corpo(adm.enviar("GET", "/api/adm/pagamentos/eventos?status=RECEBIDO&size=100", null));
        List<Integer> ids = JsonPath.read(
                lista,
                "$.itens[?(@.idExterno in ['%s','%s'] && @.gatewayId == %d)].id"
                        .formatted(primeiro, segundo, gateway.id()));
        assertThat(ids).hasSize(2);

        CountDownLatch largada = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = CompletableFuture.runAsync(() -> esperarEProcessar(largada, ids.get(0)), executor);
            var b = CompletableFuture.runAsync(() -> esperarEProcessar(largada, ids.get(1)), executor);
            largada.countDown();
            CompletableFuture.allOf(a, b).join();
        }
        processarFila();

        String detalhe = CursoITHelper.corpo(adm.enviar(
                "GET",
                "/api/adm/pagamentos/pedidos/" + pedidoNoPainel(aluno.email()).get("id"),
                null));
        assertThat(JsonPath.<List<String>>read(detalhe, "$.historico[*].para")).containsExactly("CRIADO", "APROVADO");
        assertThat(JsonPath.<List<String>>read(detalhe, "$.eventos[*].status"))
                .containsExactlyInAnyOrder("PROCESSADO", "IGNORADO");
        assertThat(mailpit.aguardarEmailPara(aluno.email()).Subject()).isEqualTo("Sua matrícula foi confirmada");
        assertThat(mailpit.quantidadePara(aluno.email())).isEqualTo(1);
    }

    private void esperarEProcessar(CountDownLatch largada, int eventoId) {
        try {
            largada.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        processamento.processar((long) eventoId);
    }

    @Test
    @DisplayName("venda de oferta ainda não cadastrada é ignorada; cadastrada a oferta, o reprocessamento matricula")
    void reprocessaDepoisDeCadastrarAOferta() {
        String outroProduto = stamp();
        String email = stamp() + "@teste.celke.uno";
        String id = "evento-" + stamp();
        notificar(pagamentos
                .notificacao("PURCHASE_APPROVED")
                .id(id)
                .xcod(null)
                .transacao("HP-" + stamp())
                .produto(outroProduto, "nova")
                .comprador(email, "Compradora"));
        List<Integer> ids = JsonPath.read(
                CursoITHelper.corpo(adm.enviar("GET", "/api/adm/pagamentos/eventos?status=IGNORADO&size=100", null)),
                "$.itens[?(@.idExterno == '%s')].id".formatted(id));
        assertThat(ids).hasSize(1);

        pagamentos.oferta(turma, outroProduto, "nova", null);
        assertThat(adm.enviar("POST", "/api/adm/pagamentos/eventos/" + ids.getFirst() + "/reprocessamento", null))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("RECEBIDO");
        processarFila();

        assertThat(adm.enviar("GET", "/api/adm/pagamentos/eventos/" + ids.getFirst(), null))
                .bodyJson()
                .extractingPath("$.evento.status")
                .isEqualTo("PROCESSADO");
        assertThat(pedidoNoPainel(email)).containsEntry("status", "APROVADO");
        assertThat(adm.enviar("POST", "/api/adm/pagamentos/eventos/" + ids.getFirst() + "/reprocessamento", null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Só eventos com erro ou ignorados podem ser reprocessados.");
    }

    @Test
    @DisplayName("checkout de oferta inativa 404; de gateway desativado 409; pedido inexistente 404")
    void checkoutRecusado() {
        String token = login(UsuariosSeed.ALUNO_EMAIL, UsuariosSeed.SENHA);
        adm.enviar("PATCH", "/api/adm/ofertas/" + oferta + "/status", "{\"ativa\":false}");
        assertThat(mvc.post()
                        .uri("/api/pagamentos/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ofertaId\":%d}".formatted(oferta)))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Oferta não encontrada.");

        adm.enviar("PATCH", "/api/adm/ofertas/" + oferta + "/status", "{\"ativa\":true}");
        adm.enviar("DELETE", "/api/adm/pagamentos/gateways/" + gateway.id() + "/ativacao", null);
        assertThat(mvc.post()
                        .uri("/api/pagamentos/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ofertaId\":%d}".formatted(oferta)))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Esta oferta não está à venda no momento.");
        assertThat(situacao("nao-existe", null)).hasStatus(HttpStatus.NOT_FOUND);
    }
}
