package br.com.celke.cursos.pagamento.infra;

import br.com.celke.cursos.pagamento.domain.EventoRecebido;
import br.com.celke.cursos.pagamento.domain.StatusEvento;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface EventoRecebidoRepository extends JpaRepository<EventoRecebido, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EventoRecebido e where e.id = :id")
    Optional<EventoRecebido> travar(Long id);

    List<EventoRecebido> findByPedidoIdOrderById(Long pedidoId);

    Page<EventoRecebido> findByStatus(StatusEvento status, Pageable pageable);
}
