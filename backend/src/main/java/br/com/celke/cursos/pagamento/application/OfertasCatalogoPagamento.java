package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.curso.application.OfertasCatalogo;
import br.com.celke.cursos.pagamento.infra.GatewayRepository;
import br.com.celke.cursos.pagamento.infra.OfertaRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Ofertas que o catálogo mostra: as ativas da turma no meio de pagamento ativo, da mais barata à mais cara. */
@Component
public class OfertasCatalogoPagamento implements OfertasCatalogo {

    private final GatewayRepository gateways;
    private final OfertaRepository ofertas;

    public OfertasCatalogoPagamento(GatewayRepository gateways, OfertaRepository ofertas) {
        this.gateways = gateways;
        this.ofertas = ofertas;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OfertaCatalogo> ativas(Long turmaId) {
        return gateways
                .findByAtivoTrue()
                .map(gateway -> ofertas.findByTurmaIdAndGatewayIdAndAtivaTrueOrderByPrecoCentavosAscIdAsc(
                        turmaId, gateway.getId()))
                .orElse(List.of())
                .stream()
                .map(o -> new OfertaCatalogo(o.getId(), o.getTitulo(), o.getPrecoCentavos(), o.getMoeda()))
                .toList();
    }
}
