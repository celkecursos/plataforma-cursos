package br.com.celke.cursos.shared.fila;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fila de jobs no PostgreSQL. A reserva usa {@code FOR UPDATE SKIP LOCKED}: vários workers consomem
 * ao mesmo tempo sem pegar o mesmo job. Todo método recebe o instante atual, para as regras de tempo
 * serem testáveis.
 */
@Service
@EnableConfigurationProperties(FilaProperties.class)
public class FilaService {

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final FilaProperties props;
    private final Backoff backoff;

    public FilaService(JdbcClient jdbc, JsonMapper jsonMapper, FilaProperties props) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.props = props;
        this.backoff = new Backoff(props.backoffBase(), props.backoffMaximo());
    }

    /** Coloca um job na fila; o payload é gravado como JSON. Devolve o id do job. */
    public long enfileirar(String tipo, Object payload, Instant agora) {
        return jdbc.sql("""
                        INSERT INTO jobs (tipo, payload, status, max_tentativas, disponivel_em, criado_em, atualizado_em)
                        VALUES (:tipo, CAST(:payload AS JSONB), 'PENDENTE', :max, :agora, :agora, :agora)
                        RETURNING id
                        """)
                .param("tipo", tipo)
                .param("payload", jsonMapper.writeValueAsString(payload))
                .param("max", props.maxTentativas())
                .param("agora", utc(agora))
                .query(Long.class)
                .single();
    }

    /**
     * Reserva o próximo job disponível de um dos tipos: pendente e no horário, ou em processamento
     * com o lease vencido (o worker anterior caiu).
     */
    @Transactional
    public Optional<Job> reservar(Collection<String> tipos, Instant agora) {
        if (tipos.isEmpty()) {
            return Optional.empty();
        }
        return jdbc.sql("""
                        UPDATE jobs
                           SET status = 'PROCESSANDO', tentativas = tentativas + 1,
                               reservado_ate = :reservadoAte, atualizado_em = :agora
                         WHERE id = (SELECT id FROM jobs
                                      WHERE tipo IN (:tipos)
                                        AND ((status = 'PENDENTE' AND disponivel_em <= :agora)
                                          OR (status = 'PROCESSANDO' AND reservado_ate < :agora))
                                      ORDER BY disponivel_em, id
                                      LIMIT 1
                                      FOR UPDATE SKIP LOCKED)
                        RETURNING id, tipo, CAST(payload AS TEXT) AS payload, tentativas, max_tentativas
                        """)
                .param("tipos", tipos)
                .param("agora", utc(agora))
                .param("reservadoAte", utc(agora.plus(props.lease())))
                .query((rs, n) -> new Job(
                        rs.getLong("id"),
                        rs.getString("tipo"),
                        rs.getString("payload"),
                        rs.getInt("tentativas"),
                        rs.getInt("max_tentativas")))
                .optional();
    }

    public void concluir(Job job, Instant agora) {
        jdbc.sql("""
                        UPDATE jobs SET status = 'CONCLUIDO', reservado_ate = NULL, ultimo_erro = NULL,
                                        atualizado_em = :agora
                         WHERE id = :id
                        """).param("id", job.id()).param("agora", utc(agora)).update();
    }

    /**
     * Estende o lease de um job em processamento: um job longo (como uma transcodificação) chama a
     * cada minuto para não ser pego por outro worker. Se o worker cair, o lease vence e o job volta.
     */
    public void renovar(Job job, Instant agora) {
        jdbc.sql("""
                        UPDATE jobs SET reservado_ate = :reservadoAte, atualizado_em = :agora
                         WHERE id = :id AND status = 'PROCESSANDO'
                        """)
                .param("id", job.id())
                .param("reservadoAte", utc(agora.plus(props.lease())))
                .param("agora", utc(agora))
                .update();
    }

    /** Falha com nova tentativa depois do backoff; sem tentativas restantes, o job vai para ERRO. */
    public void falhar(Job job, String erro, Instant agora) {
        if (job.ultimaTentativa()) {
            falharDefinitivo(job, erro, agora);
            return;
        }
        jdbc.sql("""
                        UPDATE jobs SET status = 'PENDENTE', reservado_ate = NULL, ultimo_erro = :erro,
                                        disponivel_em = :disponivelEm, atualizado_em = :agora
                         WHERE id = :id
                        """)
                .param("id", job.id())
                .param("erro", erro)
                .param("disponivelEm", utc(agora.plus(backoff.atraso(job.tentativas()))))
                .param("agora", utc(agora))
                .update();
    }

    /** Falha sem nova tentativa (erro permanente ou tentativas esgotadas). */
    public void falharDefinitivo(Job job, String erro, Instant agora) {
        jdbc.sql("""
                        UPDATE jobs SET status = 'ERRO', reservado_ate = NULL, ultimo_erro = :erro,
                                        atualizado_em = :agora
                         WHERE id = :id
                        """)
                .param("id", job.id())
                .param("erro", erro)
                .param("agora", utc(agora))
                .update();
    }

    private static OffsetDateTime utc(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }
}
