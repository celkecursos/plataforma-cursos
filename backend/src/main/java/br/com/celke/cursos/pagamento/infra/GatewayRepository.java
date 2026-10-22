package br.com.celke.cursos.pagamento.infra;

import br.com.celke.cursos.pagamento.domain.Gateway;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface GatewayRepository extends JpaRepository<Gateway, Long> {

    Optional<Gateway> findByWebhookToken(String webhookToken);

    /** O gateway dos novos checkouts; o banco garante no máximo um. */
    Optional<Gateway> findByAtivoTrue();

    List<Gateway> findAllByOrderById();

    /** Desativa os outros antes de ativar um (o índice único parcial não aceita dois ativos nem por um instante). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Gateway g set g.ativo = false where g.ativo = true and g.id <> :id")
    int desativarOutros(Long id);
}
