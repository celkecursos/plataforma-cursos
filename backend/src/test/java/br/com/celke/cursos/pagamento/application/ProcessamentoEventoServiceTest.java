package br.com.celke.cursos.pagamento.application;

import static br.com.celke.cursos.support.fixtures.UsuarioFixture.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.auth.application.ContaProperties;
import br.com.celke.cursos.auth.application.RecuperacaoSenhaService;
import br.com.celke.cursos.auth.application.RecuperacaoSenhaService.LinkSenha;
import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.matricula.application.MatriculaPagamentoService;
import br.com.celke.cursos.matricula.application.MatriculaPagamentoService.Concessao;
import br.com.celke.cursos.matricula.application.MatriculaPagamentoService.NovaConcessao;
import br.com.celke.cursos.matricula.domain.FormaPagamento;
import br.com.celke.cursos.pagamento.domain.EventoPagamento;
import br.com.celke.cursos.pagamento.domain.EventoPagamento.CompradorEvento;
import br.com.celke.cursos.pagamento.domain.EventoRecebido;
import br.com.celke.cursos.pagamento.domain.Oferta;
import br.com.celke.cursos.pagamento.domain.OrigemPedido;
import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.PedidoHistorico;
import br.com.celke.cursos.pagamento.domain.StatusEvento;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.pagamento.domain.TransicoesPedido;
import br.com.celke.cursos.pagamento.infra.EventoRecebidoRepository;
import br.com.celke.cursos.pagamento.infra.OfertaRepository;
import br.com.celke.cursos.pagamento.infra.PedidoHistoricoRepository;
import br.com.celke.cursos.pagamento.infra.PedidoRepository;
import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.shared.fila.ErroPermanenteException;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ProcessamentoEventoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final Instant OCORRIDO = AGORA.minus(Duration.ofMinutes(5));
    private static final Long GATEWAY = 1L;

    @Mock
    private EventoRecebidoRepository eventos;

    @Mock
    private PedidoRepository pedidos;

    @Mock
    private PedidoHistoricoRepository historico;

    @Mock
    private OfertaRepository ofertas;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private MatriculaPagamentoService matriculaPagamentoService;

    @Mock
    private ConteudoTurmaService conteudoTurmaService;

    @Mock
    private RecuperacaoSenhaService recuperacaoSenhaService;

    @Mock
    private EmailService emailService;

    @Mock
    private AvisosPagamento avisos;

    private final JsonMapper json = JsonMapper.builder().build();
    private ProcessamentoEventoService service;
    private Oferta oferta;

    @BeforeEach
    void setUp() {
        service = new ProcessamentoEventoService(
                eventos,
                pedidos,
                historico,
                ofertas,
                usuarioService,
                matriculaPagamentoService,
                conteudoTurmaService,
                recuperacaoSenhaService,
                emailService,
                avisos,
                new TransicoesPedido(false),
                new ContaProperties("https://aluno.celke.uno", Duration.ofHours(48), Duration.ofHours(1), 3),
                json,
                Clock.fixed(AGORA, ZoneOffset.UTC));
        oferta = new Oferta(
                3L, GATEWAY, new Oferta.DadosOferta("4774438", "k2", null, "À vista", 19_700, "BRL", 365), AGORA);
        ReflectionTestUtils.setField(oferta, "id", 20L);
    }

    private static EventoPagamento dados(StatusPedido status, String xcod, String email) {
        return new EventoPagamento(
                "ev-1",
                "PURCHASE_X",
                OCORRIDO,
                "HP1",
                status,
                xcod,
                "4774438",
                "k2",
                email == null ? null : new CompradorEvento(email, "Ana", null),
                19_700L,
                "BRL",
                FormaPagamento.PIX,
                null);
    }

    private EventoRecebido evento(EventoPagamento dados, StatusEvento status) {
        EventoRecebido evento = BeanUtils.instantiateClass(EventoRecebido.class);
        ReflectionTestUtils.setField(evento, "id", 100L);
        ReflectionTestUtils.setField(evento, "gatewayId", GATEWAY);
        ReflectionTestUtils.setField(evento, "status", status);
        ReflectionTestUtils.setField(evento, "dados", dados == null ? null : json.writeValueAsString(dados));
        when(eventos.travar(100L)).thenReturn(Optional.of(evento));
        return evento;
    }

    private Pedido pedidoDoCheckout(StatusPedido status) {
        Pedido pedido = Pedido.doCheckout(oferta, 7L, "ana@celke.uno", "Ana", AGORA.minusSeconds(3600));
        ReflectionTestUtils.setField(pedido, "id", 30L);
        ReflectionTestUtils.setField(pedido, "status", status);
        return pedido;
    }

    @Test
    @DisplayName("evento inexistente é erro permanente; já processado não faz nada")
    void inexistenteOuProcessado() {
        when(eventos.travar(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.processar(99L)).isInstanceOf(ErroPermanenteException.class);

        evento(dados(StatusPedido.APROVADO, null, null), StatusEvento.PROCESSADO);
        service.processar(100L);
        verifyNoInteractions(pedidos, matriculaPagamentoService, emailService);
    }

    @Test
    @DisplayName("sem dados, o evento vai para erro; evento que não muda o pedido é ignorado")
    void semDadosOuNaoTratado() {
        EventoRecebido semDados = evento(null, StatusEvento.RECEBIDO);
        service.processar(100L);
        assertThat(semDados.getStatus()).isEqualTo(StatusEvento.ERRO);

        EventoRecebido naoTratado = evento(dados(null, null, null), StatusEvento.RECEBIDO);
        service.processar(100L);
        assertThat(naoTratado.getStatus()).isEqualTo(StatusEvento.IGNORADO);
        assertThat(naoTratado.getErro()).isEqualTo("O evento PURCHASE_X não muda o pedido.");
        verifyNoInteractions(pedidos);
    }

    @Test
    @DisplayName("venda sem pedido e de oferta não cadastrada: ignorada com o motivo")
    void ofertaNaoCadastrada() {
        EventoRecebido evento = evento(dados(StatusPedido.APROVADO, null, "ana@celke.uno"), StatusEvento.RECEBIDO);
        when(pedidos.travarPorTransacao(GATEWAY, "HP1")).thenReturn(Optional.empty());
        when(ofertas.findByGatewayIdAndProdutoExternoAndOfertaExterna(GATEWAY, "4774438", "k2"))
                .thenReturn(Optional.empty());

        service.processar(100L);

        assertThat(evento.getStatus()).isEqualTo(StatusEvento.IGNORADO);
        assertThat(evento.getErro()).isEqualTo("Oferta não cadastrada (produto 4774438, oferta k2).");
        verify(pedidos, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("venda sem pedido e sem e-mail do comprador (afiliado): ignorada")
    void semComprador() {
        EventoRecebido evento = evento(dados(StatusPedido.APROVADO, null, null), StatusEvento.RECEBIDO);
        when(pedidos.travarPorTransacao(GATEWAY, "HP1")).thenReturn(Optional.empty());
        when(ofertas.findByGatewayIdAndProdutoExternoAndOfertaExterna(GATEWAY, "4774438", "k2"))
                .thenReturn(Optional.of(oferta));

        service.processar(100L);

        assertThat(evento.getErro()).isEqualTo("A notificação não traz o e-mail do comprador.");
    }

    @Test
    @DisplayName("venda externa aprovada: cria o pedido, a conta sem senha, a matrícula e manda o link de criar senha")
    void vendaExterna() {
        EventoRecebido evento = evento(dados(StatusPedido.APROVADO, null, "nova@celke.uno"), StatusEvento.RECEBIDO);
        when(pedidos.travarPorTransacao(GATEWAY, "HP1")).thenReturn(Optional.empty());
        when(ofertas.findByGatewayIdAndProdutoExternoAndOfertaExterna(GATEWAY, "4774438", "k2"))
                .thenReturn(Optional.of(oferta));
        when(pedidos.saveAndFlush(any())).thenAnswer(chamada -> {
            Pedido p = chamada.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 31L);
            return p;
        });
        when(ofertas.findById(20L)).thenReturn(Optional.of(oferta));
        when(usuarioService.buscarPorEmail("nova@celke.uno")).thenReturn(Optional.empty());
        Usuario nova = usuario(8, "nova@celke.uno", null, Papel.ALUNO);
        when(usuarioService.criarAlunoSemSenha("Ana", "nova@celke.uno")).thenReturn(nova);
        when(matriculaPagamentoService.conceder(
                        new NovaConcessao(8L, 3L, OCORRIDO, 365, FormaPagamento.PIX, "HP1", 31L, null)))
                .thenReturn(new Concessao(50L, true));
        when(conteudoTurmaService.turma(3L)).thenReturn(Optional.empty());
        when(recuperacaoSenhaService.linkCriarSenha(8L))
                .thenReturn(Optional.of(new LinkSenha("https://aluno.celke.uno/redefinir-senha?token=t", "1 hora")));

        service.processar(100L);

        ArgumentCaptor<Pedido> pedido = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidos).saveAndFlush(pedido.capture());
        assertThat(pedido.getValue().getOrigem()).isEqualTo(OrigemPedido.EXTERNO);
        assertThat(pedido.getValue().getStatus()).isEqualTo(StatusPedido.APROVADO);
        assertThat(pedido.getValue().getUsuarioId()).isEqualTo(8L);
        assertThat(pedido.getValue().getMatriculaId()).isEqualTo(50L);
        assertThat(evento.getStatus()).isEqualTo(StatusEvento.PROCESSADO);
        assertThat(evento.getPedidoId()).isEqualTo(31L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variaveis = ArgumentCaptor.forClass(Map.class);
        verify(emailService)
                .enviar(
                        eq("nova@celke.uno"),
                        eq("Sua matrícula foi confirmada"),
                        eq("matricula-confirmada"),
                        variaveis.capture());
        assertThat(variaveis.getValue())
                .containsEntry("curso", "seu curso")
                .containsEntry("linkCurso", "https://aluno.celke.uno/turmas/3")
                .containsEntry("linkSenha", "https://aluno.celke.uno/redefinir-senha?token=t")
                .containsEntry("validade", "1 hora");
        verify(historico, times(2)).save(any(PedidoHistorico.class));
    }

    @Test
    @DisplayName("pedido do checkout reembolsado: cancela a matrícula, sem e-mail ao aluno")
    void reembolso() {
        evento(dados(StatusPedido.REEMBOLSADO, "codigo", "ana@celke.uno"), StatusEvento.RECEBIDO);
        Pedido pedido = pedidoDoCheckout(StatusPedido.APROVADO);
        pedido.vincularMatricula(50L, AGORA);
        when(pedidos.travarPorCodigo("codigo")).thenReturn(Optional.of(pedido));

        service.processar(100L);

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.REEMBOLSADO);
        assertThat(pedido.getTransacaoExterna()).isEqualTo("HP1");
        verify(matriculaPagamentoService).cancelar(50L);
        verify(emailService, never()).enviar(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("aprovado depois de reembolsado não reabre: evento ignorado com o motivo")
    void naoReabre() {
        EventoRecebido evento = evento(dados(StatusPedido.APROVADO, "codigo", "ana@celke.uno"), StatusEvento.RECEBIDO);
        when(pedidos.travarPorCodigo("codigo")).thenReturn(Optional.of(pedidoDoCheckout(StatusPedido.REEMBOLSADO)));

        service.processar(100L);

        assertThat(evento.getStatus()).isEqualTo(StatusEvento.IGNORADO);
        assertThat(evento.getErro()).isEqualTo("Mudança de REEMBOLSADO para APROVADO não é permitida.");
        verifyNoInteractions(matriculaPagamentoService, historico);
    }

    @Test
    @DisplayName("xcod de um pedido de outra transação: segue pela transação conhecida")
    void xcodDeOutraTransacao() {
        evento(dados(StatusPedido.CHARGEBACK, "codigo", "ana@celke.uno"), StatusEvento.RECEBIDO);
        Pedido outro = pedidoDoCheckout(StatusPedido.APROVADO);
        outro.vincularTransacao("HP-OUTRA", AGORA);
        Pedido certo = pedidoDoCheckout(StatusPedido.APROVADO);
        when(pedidos.travarPorCodigo("codigo")).thenReturn(Optional.of(outro));
        when(pedidos.travarPorTransacao(GATEWAY, "HP1")).thenReturn(Optional.of(certo));

        service.processar(100L);

        assertThat(certo.getStatus()).isEqualTo(StatusPedido.CHARGEBACK);
        assertThat(outro.getStatus()).isEqualTo(StatusPedido.APROVADO);
        verify(avisos).pedidoContestado(certo, StatusPedido.CHARGEBACK);
    }

    @Test
    @DisplayName("aluno com senha: confirmação sem link; acesso já concedido não manda de novo")
    void alunoComSenha() {
        evento(dados(StatusPedido.APROVADO, "codigo", "ana@celke.uno"), StatusEvento.RECEBIDO);
        Pedido pedido = pedidoDoCheckout(StatusPedido.CRIADO);
        when(pedidos.travarPorCodigo("codigo")).thenReturn(Optional.of(pedido));
        when(ofertas.findById(20L)).thenReturn(Optional.of(oferta));
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(usuario(7, "ana@celke.uno", "hash", Papel.ALUNO)));
        when(matriculaPagamentoService.conceder(any())).thenReturn(new Concessao(50L, true));
        when(conteudoTurmaService.turma(3L)).thenReturn(Optional.empty());

        service.processar(100L);

        verify(recuperacaoSenhaService, never()).linkCriarSenha(any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variaveis = ArgumentCaptor.forClass(Map.class);
        verify(emailService).enviar(eq("ana@celke.uno"), anyString(), anyString(), variaveis.capture());
        assertThat(variaveis.getValue()).doesNotContainKey("linkSenha");
    }

    @Test
    @DisplayName("desistência: evento em erro e aviso ao admin")
    void falhar() {
        EventoRecebido evento = BeanUtils.instantiateClass(EventoRecebido.class);
        ReflectionTestUtils.setField(evento, "id", 100L);
        ReflectionTestUtils.setField(evento, "tipoOriginal", "PURCHASE_APPROVED");
        when(eventos.findById(100L)).thenReturn(Optional.of(evento));

        service.falhar(100L, "banco fora");

        assertThat(evento.getStatus()).isEqualTo(StatusEvento.ERRO);
        verify(avisos).eventoComErro(100L, "PURCHASE_APPROVED", "banco fora");
    }

    @Test
    @DisplayName("suspensão por atraso cancela a matrícula ativa e registra no histórico; sem matrícula ativa, nada")
    void suspenderPorAtraso() {
        Pedido atrasado = pedidoDoCheckout(StatusPedido.ATRASADO);
        atrasado.vincularMatricula(50L, AGORA);
        when(pedidos.travar(30L)).thenReturn(Optional.of(atrasado));
        when(matriculaPagamentoService.ativa(50L)).thenReturn(true, false);

        service.suspenderPorAtraso(30L);
        service.suspenderPorAtraso(30L);

        verify(matriculaPagamentoService).cancelar(50L);
        verify(historico).save(any(PedidoHistorico.class));
    }
}
