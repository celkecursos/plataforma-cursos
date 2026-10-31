package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.pagamento.domain.Capacidade;
import br.com.celke.cursos.pagamento.domain.ConfiguracaoGateway;
import br.com.celke.cursos.pagamento.domain.EventoPagamento;
import br.com.celke.cursos.pagamento.domain.Gateway;
import br.com.celke.cursos.pagamento.domain.GatewayPagamento;
import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.pagamento.infra.GatewayRepository;
import br.com.celke.cursos.pagamento.infra.PedidoRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Reconciliação: consulta no meio de pagamento os pedidos que podem ter perdido uma notificação e,
 * se o status mudou, grava um evento sintético que segue o mesmo caminho do webhook. Pedidos ainda
 * pendentes depois de {@code pendenteApos} e pedidos pagos sem notificação há {@code semEventoApos}
 * são consultados, até {@code janela} sem mudança. Pedido sem transação (o aluno não chegou a
 * pagar) não tem o que consultar.
 */
@Service
public class ReconciliacaoService {

    static final int LIMITE_POR_CONSULTA = 200;
    private static final Logger LOG = LoggerFactory.getLogger(ReconciliacaoService.class);

    private final GatewayRepository gateways;
    private final PedidoRepository pedidos;
    private final GatewaysRegistrados registrados;
    private final ConfiguracaoGatewayService configuracoes;
    private final RegistroEventos registro;
    private final PagamentosProperties props;
    private final Clock clock;

    public ReconciliacaoService(
            GatewayRepository gateways,
            PedidoRepository pedidos,
            GatewaysRegistrados registrados,
            ConfiguracaoGatewayService configuracoes,
            RegistroEventos registro,
            PagamentosProperties props,
            Clock clock) {
        this.gateways = gateways;
        this.pedidos = pedidos;
        this.registrados = registrados;
        this.configuracoes = configuracoes;
        this.registro = registro;
        this.props = props;
        this.clock = clock;
    }

    /** Devolve quantos eventos sintéticos foram gravados. */
    public int reconciliar() {
        int gravados = 0;
        for (Gateway gateway : gateways.findAllByOrderById()) {
            GatewayPagamento implementacao = registrados.de(gateway.getTipo());
            if (!implementacao.capacidades().contains(Capacidade.CONSULTA_API)) {
                continue;
            }
            ConfiguracaoGateway configuracao;
            try {
                configuracao = configuracoes.ler(gateway);
            } catch (IllegalStateException e) {
                LOG.warn("Reconciliação do gateway {} ignorada: {}", gateway.getId(), e.getMessage());
                continue;
            }
            for (Pedido pedido : candidatos(gateway.getId())) {
                gravados += reconciliar(gateway, implementacao, configuracao, pedido) ? 1 : 0;
            }
        }
        return gravados;
    }

    private List<Pedido> candidatos(Long gatewayId) {
        Instant agora = clock.instant();
        Instant limiteJanela = agora.minus(props.reconciliacao().janela());
        PageRequest limite = PageRequest.of(0, LIMITE_POR_CONSULTA);
        List<Pedido> candidatos = new ArrayList<>(pedidos.paraReconciliar(
                gatewayId,
                EnumSet.of(StatusPedido.CRIADO, StatusPedido.AGUARDANDO_PAGAMENTO),
                agora.minus(props.reconciliacao().pendenteApos()),
                limiteJanela,
                limite));
        candidatos.addAll(pedidos.paraReconciliar(
                gatewayId,
                EnumSet.of(StatusPedido.APROVADO, StatusPedido.ATRASADO, StatusPedido.EM_DISPUTA),
                agora.minus(props.reconciliacao().semEventoApos()),
                limiteJanela,
                limite));
        return candidatos;
    }

    private boolean reconciliar(
            Gateway gateway, GatewayPagamento implementacao, ConfiguracaoGateway configuracao, Pedido pedido) {
        try {
            Optional<EventoPagamento> situacao = implementacao
                    .consultar(pedido.getTransacaoExterna(), configuracao)
                    .filter(e -> e.statusPedido() != null && e.statusPedido() != pedido.getStatus());
            if (situacao.isEmpty()) {
                return false;
            }
            LOG.info(
                    "Reconciliação: pedido {} está {} no meio de pagamento (aqui {})",
                    pedido.getCodigo(),
                    situacao.get().statusPedido(),
                    pedido.getStatus());
            return registro.registrar(gateway.getId(), situacao.get(), null, clock.instant())
                    .isPresent();
        } catch (RuntimeException e) {
            LOG.warn("Reconciliação do pedido {} falhou: {}", pedido.getCodigo(), e.toString());
            return false;
        }
    }
}
