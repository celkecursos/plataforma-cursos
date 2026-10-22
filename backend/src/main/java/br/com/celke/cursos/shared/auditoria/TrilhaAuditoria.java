package br.com.celke.cursos.shared.auditoria;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Grava uma linha da trilha de auditoria. Usa a conexão da transação em andamento: se a mudança é
 * desfeita, o registro também é.
 */
@Component
public class TrilhaAuditoria {

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public TrilhaAuditoria(JdbcClient jdbc, JsonMapper jsonMapper, Clock clock) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    public void registrar(
            OperacaoAuditoria operacao,
            String entidade,
            String entidadeId,
            Map<String, Object> antes,
            Map<String, Object> depois,
            ContextoAuditoria contexto) {
        jdbc.sql("""
                        INSERT INTO auditoria (usuario_id, operacao, entidade, entidade_id, antes, depois, origem, ip,
                                               user_agent, request_id, em)
                        VALUES (:usuario, :operacao, :entidade, :entidadeId, CAST(:antes AS JSONB), CAST(:depois AS JSONB),
                                :origem, :ip, :userAgent, :requestId, :em)
                        """)
                .param("usuario", contexto.usuarioId())
                .param("operacao", operacao.name())
                .param("entidade", entidade)
                .param("entidadeId", entidadeId)
                .param("antes", antes == null ? null : jsonMapper.writeValueAsString(antes))
                .param("depois", depois == null ? null : jsonMapper.writeValueAsString(depois))
                .param("origem", contexto.origem())
                .param("ip", contexto.ip())
                .param("userAgent", contexto.userAgent())
                .param("requestId", contexto.requestId())
                .param("em", utc(clock.instant()))
                .update();
    }

    private static OffsetDateTime utc(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }
}
