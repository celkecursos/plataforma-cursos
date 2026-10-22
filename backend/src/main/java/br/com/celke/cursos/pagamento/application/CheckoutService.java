package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.TurmaResumo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.pagamento.domain.Checkout;
import br.com.celke.cursos.pagamento.domain.Comprador;
import br.com.celke.cursos.pagamento.domain.Gateway;
import br.com.celke.cursos.pagamento.domain.Oferta;
import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.PedidoHistorico;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.pagamento.infra.GatewayRepository;
import br.com.celke.cursos.pagamento.infra.OfertaRepository;
import br.com.celke.cursos.pagamento.infra.PedidoHistoricoRepository;
import br.com.celke.cursos.pagamento.infra.PedidoRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Botão "Comprar" (abre o pedido e monta o checkout) e a página de retorno (situação do pedido). */
@Service
public class CheckoutService {

    private final OfertaRepository ofertas;
    private final GatewayRepository gateways;
    private final PedidoRepository pedidos;
    private final PedidoHistoricoRepository historico;
    private final GatewaysRegistrados registrados;
    private final ConfiguracaoGatewayService configuracoes;
    private final ConteudoTurmaService conteudoTurmaService;
    private final UsuarioService usuarioService;
    private final Clock clock;

    public CheckoutService(
            OfertaRepository ofertas,
            GatewayRepository gateways,
            PedidoRepository pedidos,
            PedidoHistoricoRepository historico,
            GatewaysRegistrados registrados,
            ConfiguracaoGatewayService configuracoes,
            ConteudoTurmaService conteudoTurmaService,
            UsuarioService usuarioService,
            Clock clock) {
        this.ofertas = ofertas;
        this.gateways = gateways;
        this.pedidos = pedidos;
        this.historico = historico;
        this.registrados = registrados;
        this.configuracoes = configuracoes;
        this.conteudoTurmaService = conteudoTurmaService;
        this.usuarioService = usuarioService;
        this.clock = clock;
    }

    /** Abre o pedido com o e-mail da conta e devolve o checkout do meio de pagamento da oferta. */
    @Transactional
    public CheckoutIniciado iniciar(Long usuarioId, Long ofertaId) {
        Oferta oferta = ofertas.findById(ofertaId)
                .filter(Oferta::isAtiva)
                .orElseThrow(() -> new NaoEncontradoException("Oferta não encontrada."));
        Gateway gateway = gateways.findById(oferta.getGatewayId())
                .filter(Gateway::isAtivo)
                .orElseThrow(() -> new ConflitoException("Esta oferta não está à venda no momento."));
        TurmaResumo turma = conteudoTurmaService
                .turma(oferta.getTurmaId())
                .filter(t -> t.cursoStatus() == StatusCurso.PUBLICADO && t.turmaStatus() == StatusTurma.ATIVA)
                .orElseThrow(() -> new ConflitoException("Este curso não está recebendo matrículas no momento."));
        Usuario usuario = usuarioService.buscarPorId(usuarioId).orElseThrow();
        Instant agora = clock.instant();
        Pedido pedido =
                pedidos.save(Pedido.doCheckout(oferta, usuario.getId(), usuario.getEmail(), usuario.getNome(), agora));
        historico.save(
                new PedidoHistorico(pedido.getId(), null, StatusPedido.CRIADO, null, "Checkout iniciado", agora));
        Checkout checkout = registrados
                .de(gateway.getTipo())
                .iniciarCheckout(
                        pedido,
                        oferta,
                        new Comprador(usuario.getEmail(), usuario.getNome()),
                        configuracoes.ler(gateway));
        return new CheckoutIniciado(pedido.getCodigo(), turma.turmaId(), checkout);
    }

    /**
     * Situação do pedido para a página de retorno. Curso e turma só para o dono logado; para os outros,
     * só o status (o código sozinho não revela nada além disso).
     */
    @Transactional(readOnly = true)
    public SituacaoPedido situacao(String codigo, Long usuarioId) {
        Pedido pedido =
                pedidos.findByCodigo(codigo).orElseThrow(() -> new NaoEncontradoException("Pedido não encontrado."));
        if (usuarioId == null || !pedido.pertenceA(usuarioId)) {
            return new SituacaoPedido(pedido.getCodigo(), pedido.getStatus(), null);
        }
        TurmaResumo turma = ofertas.findById(pedido.getOfertaId())
                .flatMap(o -> conteudoTurmaService.turma(o.getTurmaId()))
                .orElse(null);
        return new SituacaoPedido(pedido.getCodigo(), pedido.getStatus(), turma);
    }

    public record CheckoutIniciado(String pedido, Long turmaId, Checkout checkout) {}

    /** @param turma nula quando quem consulta não é o dono do pedido */
    public record SituacaoPedido(String codigo, StatusPedido status, TurmaResumo turma) {}
}
