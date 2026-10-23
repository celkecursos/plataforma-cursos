package br.com.celke.cursos.pagamento.infra;

import br.com.celke.cursos.pagamento.domain.PedidoHistorico;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoHistoricoRepository extends JpaRepository<PedidoHistorico, Long> {

    List<PedidoHistorico> findByPedidoIdOrderById(Long pedidoId);
}
