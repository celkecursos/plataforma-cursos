package br.com.celke.cursos.pagamento.api;

import br.com.celke.cursos.curso.application.ConteudoTurmaService.TurmaResumo;
import br.com.celke.cursos.matricula.domain.FormaPagamento;
import br.com.celke.cursos.pagamento.application.CheckoutService.CheckoutIniciado;
import br.com.celke.cursos.pagamento.application.CheckoutService.SituacaoPedido;
import br.com.celke.cursos.pagamento.application.ConfiguracaoGatewayService.SegredoResumo;
import br.com.celke.cursos.pagamento.application.GatewayAdmService.GatewayDetalhe;
import br.com.celke.cursos.pagamento.application.OfertaAdmService.OfertaComGateway;
import br.com.celke.cursos.pagamento.application.PedidoAdmService.DetalhePedido;
import br.com.celke.cursos.pagamento.application.PedidoAdmService.PedidoComOferta;
import br.com.celke.cursos.pagamento.domain.Checkout;
import br.com.celke.cursos.pagamento.domain.EventoRecebido;
import br.com.celke.cursos.pagamento.domain.Oferta;
import br.com.celke.cursos.pagamento.domain.OrigemPedido;
import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.PedidoHistorico;
import br.com.celke.cursos.pagamento.domain.StatusEvento;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.pagamento.domain.TipoGateway;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Respostas das rotas de pagamento. Segredos dos meios de pagamento nunca saem daqui. */
public final class PagamentoResponses {

    private PagamentoResponses() {}

    /**
     * @param tipo {@code REDIRECIONAMENTO} (ir para {@code url}) ou {@code EMBUTIDO} (formulário com {@code dados})
     */
    public record CheckoutResponse(String pedido, String tipo, String url, Map<String, Object> dados) {

        public static CheckoutResponse de(CheckoutIniciado iniciado) {
            return switch (iniciado.checkout()) {
                case Checkout.Redirecionamento r ->
                    new CheckoutResponse(iniciado.pedido(), "REDIRECIONAMENTO", r.url(), null);
                case Checkout.Embutido e -> new CheckoutResponse(iniciado.pedido(), "EMBUTIDO", null, e.dados());
            };
        }
    }

    /** Situação para a página de retorno; curso e turma só para o dono do pedido. */
    public record PedidoPublico(
            String codigo, StatusPedido status, Long turmaId, String turmaNome, String cursoTitulo, String cursoSlug) {

        public static PedidoPublico de(SituacaoPedido situacao) {
            TurmaResumo turma = situacao.turma();
            return new PedidoPublico(
                    situacao.codigo(),
                    situacao.status(),
                    turma == null ? null : turma.turmaId(),
                    turma == null ? null : turma.turmaNome(),
                    turma == null ? null : turma.cursoTitulo(),
                    turma == null ? null : turma.cursoSlug());
        }
    }

    public record WebhookResponse(String resultado) {}

    /**
     * Meio de pagamento no painel.
     *
     * @param valores campos não secretos
     * @param segredos situação de cada campo secreto (configurado e os 4 últimos caracteres)
     * @param segredosLegiveis falso se a PAGAMENTOS_CHAVE falta ou mudou: informe os segredos de novo
     */
    public record GatewayResponse(
            Long id,
            TipoGateway tipo,
            String nomeTipo,
            String nome,
            boolean ativo,
            Map<String, String> valores,
            Map<String, SegredoResumo> segredos,
            boolean segredosLegiveis,
            String webhookUrl,
            Instant criadoEm,
            Instant atualizadoEm) {

        public static GatewayResponse de(GatewayDetalhe detalhe) {
            var g = detalhe.gateway();
            return new GatewayResponse(
                    g.getId(),
                    g.getTipo(),
                    detalhe.nomeTipo(),
                    g.getNome(),
                    g.isAtivo(),
                    detalhe.valores(),
                    detalhe.segredos(),
                    detalhe.segredosLegiveis(),
                    detalhe.webhookUrl(),
                    g.getCriadoEm(),
                    g.getAtualizadoEm());
        }
    }

    public record OfertaResponse(
            Long id,
            Long turmaId,
            Long gatewayId,
            String gatewayNome,
            boolean gatewayAtivo,
            String produtoExterno,
            String ofertaExterna,
            String codigoCheckout,
            String titulo,
            long precoCentavos,
            String moeda,
            Integer diasAcesso,
            boolean ativa,
            Instant criadoEm) {

        public static OfertaResponse de(OfertaComGateway item) {
            Oferta o = item.oferta();
            return new OfertaResponse(
                    o.getId(),
                    o.getTurmaId(),
                    o.getGatewayId(),
                    item.gateway() == null ? null : item.gateway().getNome(),
                    item.gateway() != null && item.gateway().isAtivo(),
                    o.getProdutoExterno(),
                    o.getOfertaExterna(),
                    o.getCodigoCheckout(),
                    o.getTitulo(),
                    o.getPrecoCentavos(),
                    o.getMoeda(),
                    o.getDiasAcesso(),
                    o.isAtiva(),
                    o.getCriadoEm());
        }
    }

    public record PedidoAdm(
            Long id,
            String codigo,
            StatusPedido status,
            OrigemPedido origem,
            String email,
            String nome,
            Long usuarioId,
            Long gatewayId,
            Long ofertaId,
            String ofertaTitulo,
            Long turmaId,
            String transacaoExterna,
            Long valorCentavos,
            String moeda,
            FormaPagamento formaPagamento,
            Long matriculaId,
            Instant criadoEm,
            Instant atualizadoEm) {

        public static PedidoAdm de(PedidoComOferta item) {
            Pedido p = item.pedido();
            return new PedidoAdm(
                    p.getId(),
                    p.getCodigo(),
                    p.getStatus(),
                    p.getOrigem(),
                    p.getEmail(),
                    p.getNome(),
                    p.getUsuarioId(),
                    p.getGatewayId(),
                    p.getOfertaId(),
                    item.oferta() == null ? null : item.oferta().getTitulo(),
                    item.oferta() == null ? null : item.oferta().getTurmaId(),
                    p.getTransacaoExterna(),
                    p.getValorCentavos(),
                    p.getMoeda(),
                    p.getFormaPagamento(),
                    p.getMatriculaId(),
                    p.getCriadoEm(),
                    p.getAtualizadoEm());
        }
    }

    public record PedidoDetalhe(PedidoAdm pedido, List<Historico> historico, List<EventoResumo> eventos) {

        public static PedidoDetalhe de(DetalhePedido detalhe) {
            return new PedidoDetalhe(
                    PedidoAdm.de(detalhe.pedido()),
                    detalhe.historico().stream().map(Historico::de).toList(),
                    detalhe.eventos().stream().map(EventoResumo::de).toList());
        }
    }

    public record Historico(Long id, StatusPedido de, StatusPedido para, Long eventoId, String motivo, Instant em) {

        static Historico de(PedidoHistorico h) {
            return new Historico(h.getId(), h.getDe(), h.getPara(), h.getEventoId(), h.getMotivo(), h.getEm());
        }
    }

    public record EventoResumo(
            Long id,
            Long gatewayId,
            String idExterno,
            String tipoOriginal,
            StatusEvento status,
            Instant recebidoEm,
            Instant ocorridoEm,
            String transacao,
            String erro,
            Long pedidoId,
            Instant processadoEm) {

        public static EventoResumo de(EventoRecebido e) {
            return new EventoResumo(
                    e.getId(),
                    e.getGatewayId(),
                    e.getIdExterno(),
                    e.getTipoOriginal(),
                    e.getStatus(),
                    e.getRecebidoEm(),
                    e.getOcorridoEm(),
                    e.getTransacao(),
                    e.getErro(),
                    e.getPedidoId(),
                    e.getProcessadoEm());
        }
    }

    /** @param payload corpo recebido em JSON (mascarado); nulo depois da retenção */
    public record EventoDetalhe(EventoResumo evento, String payload) {

        public static EventoDetalhe de(EventoRecebido e) {
            return new EventoDetalhe(EventoResumo.de(e), e.getPayload());
        }
    }
}
