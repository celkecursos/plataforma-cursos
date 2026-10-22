package br.com.celke.cursos.pagamento.infra;

import br.com.celke.cursos.pagamento.domain.Oferta;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfertaRepository extends JpaRepository<Oferta, Long> {

    List<Oferta> findByTurmaIdOrderById(Long turmaId);

    List<Oferta> findByTurmaIdAndGatewayIdAndAtivaTrueOrderByPrecoCentavosAscIdAsc(Long turmaId, Long gatewayId);

    /** A oferta de uma notificação (produto + oferta no meio de pagamento). */
    Optional<Oferta> findByGatewayIdAndProdutoExternoAndOfertaExterna(
            Long gatewayId, String produtoExterno, String ofertaExterna);

    boolean existsByGatewayIdAndProdutoExternoAndOfertaExternaAndIdNot(
            Long gatewayId, String produtoExterno, String ofertaExterna, Long id);

    boolean existsByGatewayIdAndProdutoExternoAndOfertaExterna(
            Long gatewayId, String produtoExterno, String ofertaExterna);
}
