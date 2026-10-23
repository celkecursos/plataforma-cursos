package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.pagamento.domain.EventoRecebido;
import br.com.celke.cursos.pagamento.domain.Oferta;
import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.PedidoHistorico;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.pagamento.infra.EventoRecebidoRepository;
import br.com.celke.cursos.pagamento.infra.OfertaRepository;
import br.com.celke.cursos.pagamento.infra.PedidoHistoricoRepository;
import br.com.celke.cursos.pagamento.infra.PedidoRepository;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pedidos no painel: lista com filtros e o detalhe com a linha do tempo e os eventos recebidos. */
@Service
public class PedidoAdmService {

    private final PedidoRepository pedidos;
    private final PedidoHistoricoRepository historico;
    private final EventoRecebidoRepository eventos;
    private final OfertaRepository ofertas;

    public PedidoAdmService(
            PedidoRepository pedidos,
            PedidoHistoricoRepository historico,
            EventoRecebidoRepository eventos,
            OfertaRepository ofertas) {
        this.pedidos = pedidos;
        this.historico = historico;
        this.eventos = eventos;
        this.ofertas = ofertas;
    }

    /** Mais recentes primeiro. */
    @Transactional(readOnly = true)
    public Page<PedidoComOferta> listar(FiltroPedidos filtro, Pageable pagina) {
        Page<Pedido> page = pedidos.findAll(
                especificacao(filtro),
                PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(), Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, Oferta> porId =
                ofertas
                        .findAllById(page.getContent().stream()
                                .map(Pedido::getOfertaId)
                                .distinct()
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(Oferta::getId, Function.identity()));
        return page.map(p -> new PedidoComOferta(p, porId.get(p.getOfertaId())));
    }

    @Transactional(readOnly = true)
    public DetalhePedido detalhar(Long id) {
        Pedido pedido = pedidos.findById(id).orElseThrow(() -> new NaoEncontradoException("Pedido não encontrado."));
        return new DetalhePedido(
                new PedidoComOferta(
                        pedido, ofertas.findById(pedido.getOfertaId()).orElse(null)),
                historico.findByPedidoIdOrderById(id),
                eventos.findByPedidoIdOrderById(id));
    }

    private static Specification<Pedido> especificacao(FiltroPedidos filtro) {
        Specification<Pedido> spec = Specification.unrestricted();
        if (filtro.status() != null) {
            spec = spec.and((raiz, consulta, cb) -> cb.equal(raiz.get("status"), filtro.status()));
        }
        if (filtro.gatewayId() != null) {
            spec = spec.and((raiz, consulta, cb) -> cb.equal(raiz.get("gatewayId"), filtro.gatewayId()));
        }
        if (filtro.email() != null && !filtro.email().isBlank()) {
            String trecho = "%" + filtro.email().strip().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((raiz, consulta, cb) -> cb.like(raiz.get("email"), trecho));
        }
        if (filtro.de() != null) {
            spec = spec.and((raiz, consulta, cb) -> cb.greaterThanOrEqualTo(raiz.get("criadoEm"), filtro.de()));
        }
        if (filtro.ate() != null) {
            spec = spec.and((raiz, consulta, cb) -> cb.lessThan(raiz.get("criadoEm"), filtro.ate()));
        }
        return spec;
    }

    /** Filtros opcionais da lista; o e-mail aceita um trecho. */
    public record FiltroPedidos(StatusPedido status, Long gatewayId, String email, Instant de, Instant ate) {}

    public record PedidoComOferta(Pedido pedido, Oferta oferta) {}

    public record DetalhePedido(
            PedidoComOferta pedido, List<PedidoHistorico> historico, List<EventoRecebido> eventos) {}
}
