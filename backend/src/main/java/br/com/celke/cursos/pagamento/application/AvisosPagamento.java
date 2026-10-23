package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.shared.operacao.AlertasProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Avisos de pagamento para quem administra ({@code ALERTA_EMAIL}, o mesmo dos alertas do worker):
 * chargeback, disputa e notificação com erro. Sem destinatário, ficam só no log.
 */
@Component
@EnableConfigurationProperties(AlertasProperties.class)
public class AvisosPagamento {

    private static final Logger LOG = LoggerFactory.getLogger(AvisosPagamento.class);

    private final EmailService emailService;
    private final AlertasProperties alertas;

    public AvisosPagamento(EmailService emailService, AlertasProperties alertas) {
        this.emailService = emailService;
        this.alertas = alertas;
    }

    public void pedidoContestado(Pedido pedido, StatusPedido status) {
        String titulo = status == StatusPedido.CHARGEBACK ? "Chargeback" : "Disputa aberta";
        avisar(
                "%s no pedido %s".formatted(titulo, pedido.getCodigo()),
                "Pedido %d (%s), transação %s, comprador %s. Status: %s."
                        .formatted(
                                pedido.getId(),
                                pedido.getCodigo(),
                                pedido.getTransacaoExterna(),
                                pedido.getEmail(),
                                status));
    }

    public void eventoComErro(Long eventoId, String tipo, String motivo) {
        avisar(
                "Notificação de pagamento com erro",
                "Evento %d (%s): %s\nVeja em Pagamentos → Eventos recebidos e reprocesse depois de corrigir."
                        .formatted(eventoId, tipo, motivo));
    }

    private void avisar(String titulo, String detalhes) {
        LOG.warn("{}: {}", titulo, detalhes);
        if (alertas.ligados()) {
            emailService.enviar(
                    alertas.email(),
                    "[Plataforma de Cursos] " + titulo,
                    "alerta-operacao",
                    Map.of("titulo", titulo, "detalhes", detalhes));
        }
    }
}
