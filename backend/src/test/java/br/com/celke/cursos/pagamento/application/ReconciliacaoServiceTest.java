package br.com.celke.cursos.pagamento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.pagamento.domain.Capacidade;
import br.com.celke.cursos.pagamento.domain.ConfiguracaoGateway;
import br.com.celke.cursos.pagamento.domain.EventoPagamento;
import br.com.celke.cursos.pagamento.domain.Gateway;
import br.com.celke.cursos.pagamento.domain.GatewayPagamento;
import br.com.celke.cursos.pagamento.domain.Oferta;
import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.pagamento.domain.TipoGateway;
import br.com.celke.cursos.pagamento.infra.GatewayRepository;
import br.com.celke.cursos.pagamento.infra.PedidoRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class ReconciliacaoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private GatewayRepository gateways;

    @Mock
    private PedidoRepository pedidos;

    @Mock
    private GatewayPagamento implementacao;

    @Mock
    private ConfiguracaoGatewayService configuracoes;

    @Mock
    private RegistroEventos registro;

    private ReconciliacaoService service;
    private Gateway gateway;
    private final ConfiguracaoGateway configuracao = new ConfiguracaoGateway(1L, TipoGateway.HOTMART, Map.of());

    @BeforeEach
    void setUp() {
        when(implementacao.tipo()).thenReturn(TipoGateway.HOTMART);
        var props = new PagamentosProperties(
                null,
                DataSize.ofKilobytes(256),
                600,
                Duration.ofDays(180),
                false,
                null,
                new PagamentosProperties.Reconciliacao(
                        true, Duration.ofHours(24), Duration.ofDays(3), Duration.ofDays(60)));
        service = new ReconciliacaoService(
                gateways,
                pedidos,
                new GatewaysRegistrados(List.of(implementacao)),
                configuracoes,
                registro,
                props,
                Clock.fixed(AGORA, ZoneOffset.UTC));
        gateway = new Gateway(TipoGateway.HOTMART, "Hotmart", "token", AGORA);
        ReflectionTestUtils.setField(gateway, "id", 1L);
        when(gateways.findAllByOrderById()).thenReturn(List.of(gateway));
    }

    private static Pedido pedido(StatusPedido status, String transacao) {
        Oferta oferta = new Oferta(3L, 1L, new Oferta.DadosOferta("p", "o", null, "t", 1, "BRL", null), AGORA);
        ReflectionTestUtils.setField(oferta, "id", 20L);
        Pedido pedido = Pedido.doCheckout(oferta, 7L, "ana@celke.uno", null, AGORA);
        pedido.vincularTransacao(transacao, AGORA);
        ReflectionTestUtils.setField(pedido, "status", status);
        return pedido;
    }

    private static EventoPagamento situacao(StatusPedido status) {
        return new EventoPagamento(
                "reconciliacao:HP1:" + status,
                "RECONCILIACAO",
                AGORA,
                "HP1",
                status,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    @DisplayName("consulta pendentes e pagos sem notificação; grava só quando o status mudou")
    void gravaQuandoMudou() {
        when(implementacao.capacidades()).thenReturn(Set.of(Capacidade.CONSULTA_API));
        when(configuracoes.ler(gateway)).thenReturn(configuracao);
        Pedido pendente = pedido(StatusPedido.AGUARDANDO_PAGAMENTO, "HP1");
        Pedido pago = pedido(StatusPedido.APROVADO, "HP2");
        Pedido falha = pedido(StatusPedido.APROVADO, "HP3");
        when(pedidos.paraReconciliar(
                        eq(1L),
                        eq(Set.of(StatusPedido.CRIADO, StatusPedido.AGUARDANDO_PAGAMENTO)),
                        eq(AGORA.minus(Duration.ofHours(24))),
                        eq(AGORA.minus(Duration.ofDays(60))),
                        any()))
                .thenReturn(List.of(pendente));
        when(pedidos.paraReconciliar(
                        eq(1L),
                        eq(Set.of(StatusPedido.APROVADO, StatusPedido.ATRASADO, StatusPedido.EM_DISPUTA)),
                        eq(AGORA.minus(Duration.ofDays(3))),
                        eq(AGORA.minus(Duration.ofDays(60))),
                        any()))
                .thenReturn(List.of(pago, falha));
        EventoPagamento aprovado = situacao(StatusPedido.APROVADO);
        when(implementacao.consultar("HP1", configuracao)).thenReturn(Optional.of(aprovado));
        when(implementacao.consultar("HP2", configuracao)).thenReturn(Optional.of(situacao(StatusPedido.APROVADO)));
        when(implementacao.consultar("HP3", configuracao)).thenThrow(new IllegalStateException("rede"));
        when(registro.registrar(1L, aprovado, null, AGORA)).thenReturn(Optional.of(77L));

        assertThat(service.reconciliar()).isEqualTo(1);
    }

    @Test
    @DisplayName("gateway sem consulta pela API ou com segredos ilegíveis é pulado")
    void pulaGateway() {
        when(implementacao.capacidades()).thenReturn(Set.of(Capacidade.CHECKOUT_REDIRECIONAMENTO));
        assertThat(service.reconciliar()).isZero();

        when(implementacao.capacidades()).thenReturn(Set.of(Capacidade.CONSULTA_API));
        when(configuracoes.ler(gateway)).thenThrow(new IllegalStateException("PAGAMENTOS_CHAVE não está definida."));
        assertThat(service.reconciliar()).isZero();
        verify(pedidos, never()).paraReconciliar(any(), any(), any(), any(), any());
    }
}
