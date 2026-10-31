package br.com.celke.cursos.pagamento.infra;

import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PedidoRepository extends JpaRepository<Pedido, Long>, JpaSpecificationExecutor<Pedido> {

    Optional<Pedido> findByCodigo(String codigo);

    /** Trava o pedido enquanto um evento é aplicado: dois eventos do mesmo pedido não andam juntos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pedido p where p.codigo = :codigo")
    Optional<Pedido> travarPorCodigo(String codigo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pedido p where p.gatewayId = :gatewayId and p.transacaoExterna = :transacao")
    Optional<Pedido> travarPorTransacao(Long gatewayId, String transacao);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pedido p where p.id = :id")
    Optional<Pedido> travar(Long id);

    /** Pedidos de um gateway com transação, num dos status, sem mudança entre os dois instantes. */
    @Query("""
            select p from Pedido p
             where p.gatewayId = :gatewayId and p.transacaoExterna is not null and p.status in :status
               and p.atualizadoEm < :antesDe and p.atualizadoEm >= :depoisDe
             order by p.atualizadoEm
            """)
    List<Pedido> paraReconciliar(
            Long gatewayId, Collection<StatusPedido> status, Instant antesDe, Instant depoisDe, Pageable limite);

    /** Pedidos atrasados há mais tempo que o tolerado, ainda com matrícula. */
    List<Pedido> findByStatusAndAtualizadoEmBeforeAndMatriculaIdNotNull(StatusPedido status, Instant antesDe);
}
