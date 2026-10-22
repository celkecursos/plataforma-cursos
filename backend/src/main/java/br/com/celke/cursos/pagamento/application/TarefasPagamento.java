package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.pagamento.infra.EventosPagamentoJdbc;
import br.com.celke.cursos.pagamento.infra.PedidoRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarefas agendadas dos pagamentos, só no worker: reconciliação diária (se ligada), suspensão por
 * atraso (se configurada) e retenção semanal do corpo dos eventos.
 */
@Component
@ConditionalOnBooleanProperty("app.fila.consumidor-ativo")
public class TarefasPagamento {

    private static final Logger LOG = LoggerFactory.getLogger(TarefasPagamento.class);

    private final ReconciliacaoService reconciliacao;
    private final ProcessamentoEventoService processamento;
    private final PedidoRepository pedidos;
    private final EventosPagamentoJdbc eventos;
    private final PagamentosProperties props;
    private final Clock clock;

    public TarefasPagamento(
            ReconciliacaoService reconciliacao,
            ProcessamentoEventoService processamento,
            PedidoRepository pedidos,
            EventosPagamentoJdbc eventos,
            PagamentosProperties props,
            Clock clock) {
        this.reconciliacao = reconciliacao;
        this.processamento = processamento;
        this.pedidos = pedidos;
        this.eventos = eventos;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 5 * * *", zone = "America/Sao_Paulo")
    public void reconciliar() {
        if (props.reconciliacao().ativa()) {
            LOG.info("Reconciliação de pagamentos: {} evento(s) gravado(s)", reconciliacao.reconciliar());
        }
    }

    @Scheduled(cron = "0 15 5 * * *", zone = "America/Sao_Paulo")
    public void suspenderAtrasados() {
        if (props.atrasoSuspendeApos() == null) {
            return;
        }
        for (Pedido pedido : pedidos.findByStatusAndAtualizadoEmBeforeAndMatriculaIdNotNull(
                StatusPedido.ATRASADO, clock.instant().minus(props.atrasoSuspendeApos()))) {
            processamento.suspenderPorAtraso(pedido.getId());
        }
    }

    @Scheduled(cron = "0 45 4 * * SUN", zone = "America/Sao_Paulo")
    public void aplicarRetencao() {
        int apagados = eventos.apagarPayloads(clock.instant().minus(props.retencaoPayload()));
        LOG.info("Retenção dos eventos de pagamento: {} corpo(s) apagado(s)", apagados);
    }
}
