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

    /** O que o aluno vai comprar, para confirmar antes de abrir o pedido (mesmas regras do checkout). */
    @Transactional(readOnly = true)
    public ResumoOferta resumo(Long ofertaId) {
        Oferta oferta = ofertaAVenda(ofertaId);
        Gateway gateway = gatewayAtivo(oferta);
        TurmaResumo turma = turmaAberta(oferta);
        return new ResumoOferta(
                oferta.getId(),
                oferta.getTitulo(),
                oferta.getPrecoCentavos(),
                oferta.getMoeda(),
                turma.cursoTitulo(),
                turma.turmaNome(),
                gateway.getTipo().name());
    }

    /** Abre o pedido com o e-mail da conta e devolve o checkout do meio de pagamento da oferta. */
    @Transactional
    public CheckoutIniciado iniciar(Long usuarioId, Long ofertaId) {
        Oferta oferta = ofertaAVenda(ofertaId);
        Gateway gateway = gatewayAtivo(oferta);
        TurmaResumo turma = turmaAberta(oferta);
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

    private Oferta ofertaAVenda(Long ofertaId) {
        return ofertas.findById(ofertaId)
                .filter(Oferta::isAtiva)
                .orElseThrow(() -> new NaoEncontradoException("Oferta não encontrada."));
    }

    private Gateway gatewayAtivo(Oferta oferta) {
        return gateways.findById(oferta.getGatewayId())
                .filter(Gateway::isAtivo)
                .orElseThrow(() -> new ConflitoException("Esta oferta não está à venda no momento."));
    }

    private TurmaResumo turmaAberta(Oferta oferta) {
        return conteudoTurmaService
                .turma(oferta.getTurmaId())
                .filter(t -> t.cursoStatus() == StatusCurso.PUBLICADO && t.turmaStatus() == StatusTurma.ATIVA)
                .orElseThrow(() -> new ConflitoException("Este curso não está recebendo matrículas no momento."));
    }

    public record CheckoutIniciado(String pedido, Long turmaId, Checkout checkout) {}

    /** Oferta à venda: título, preço, curso, turma e o meio de pagamento (para onde o aluno vai). */
    public record ResumoOferta(
            Long id,
            String titulo,
            long precoCentavos,
            String moeda,
            String curso,
            String turma,
            String meioPagamento) {}

    /** @param turma nula quando quem consulta não é o dono do pedido */
    public record SituacaoPedido(String codigo, StatusPedido status, TurmaResumo turma) {}
}
