package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.pagamento.domain.Gateway;
import br.com.celke.cursos.pagamento.domain.Oferta;
import br.com.celke.cursos.pagamento.domain.Oferta.DadosOferta;
import br.com.celke.cursos.pagamento.infra.GatewayRepository;
import br.com.celke.cursos.pagamento.infra.OfertaRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ofertas de uma turma (aba "Venda" do ADM). */
@Service
public class OfertaAdmService {

    private final OfertaRepository ofertas;
    private final GatewayRepository gateways;
    private final ConteudoTurmaService conteudoTurmaService;
    private final Clock clock;

    public OfertaAdmService(
            OfertaRepository ofertas,
            GatewayRepository gateways,
            ConteudoTurmaService conteudoTurmaService,
            Clock clock) {
        this.ofertas = ofertas;
        this.gateways = gateways;
        this.conteudoTurmaService = conteudoTurmaService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<OfertaComGateway> listar(Long turmaId) {
        exigirTurma(turmaId);
        Map<Long, Gateway> porId =
                gateways.findAll().stream().collect(Collectors.toMap(Gateway::getId, Function.identity()));
        return ofertas.findByTurmaIdOrderById(turmaId).stream()
                .map(o -> new OfertaComGateway(o, porId.get(o.getGatewayId())))
                .toList();
    }

    /** Sem gateway informado, a oferta fica no meio de pagamento ativo. */
    @Transactional
    public OfertaComGateway criar(Long turmaId, Long gatewayId, DadosOferta dados) {
        exigirTurma(turmaId);
        Gateway gateway = gatewayId == null
                ? gateways.findByAtivoTrue()
                        .orElseThrow(() ->
                                new ConflitoException("Configure e ative um meio de pagamento antes de criar ofertas."))
                : gateways.findById(gatewayId)
                        .orElseThrow(() -> new NaoEncontradoException("Meio de pagamento não encontrado."));
        if (ofertas.existsByGatewayIdAndProdutoExternoAndOfertaExterna(
                gateway.getId(),
                dados.produtoExterno().strip(),
                dados.ofertaExterna().strip())) {
            throw duplicada();
        }
        return new OfertaComGateway(salvar(new Oferta(turmaId, gateway.getId(), dados, clock.instant())), gateway);
    }

    @Transactional
    public OfertaComGateway atualizar(Long id, DadosOferta dados) {
        Oferta oferta = oferta(id);
        if (ofertas.existsByGatewayIdAndProdutoExternoAndOfertaExternaAndIdNot(
                oferta.getGatewayId(),
                dados.produtoExterno().strip(),
                dados.ofertaExterna().strip(),
                id)) {
            throw duplicada();
        }
        oferta.atualizar(dados, clock.instant());
        return new OfertaComGateway(
                salvar(oferta), gateways.findById(oferta.getGatewayId()).orElse(null));
    }

    @Transactional
    public OfertaComGateway alterarAtiva(Long id, boolean ativa) {
        Oferta oferta = oferta(id);
        oferta.alterarAtiva(ativa, clock.instant());
        return new OfertaComGateway(
                oferta, gateways.findById(oferta.getGatewayId()).orElse(null));
    }

    private Oferta salvar(Oferta oferta) {
        try {
            // O único do banco decide quando duas ofertas iguais chegam ao mesmo tempo
            return ofertas.saveAndFlush(oferta);
        } catch (DataIntegrityViolationException e) {
            throw duplicada();
        }
    }

    private Oferta oferta(Long id) {
        return ofertas.findById(id).orElseThrow(() -> new NaoEncontradoException("Oferta não encontrada."));
    }

    private void exigirTurma(Long turmaId) {
        if (conteudoTurmaService.turma(turmaId).isEmpty()) {
            throw new NaoEncontradoException("Turma não encontrada.");
        }
    }

    private static ConflitoException duplicada() {
        return new ConflitoException("Já existe uma oferta com este produto e esta oferta no meio de pagamento.");
    }

    public record OfertaComGateway(Oferta oferta, Gateway gateway) {}
}
