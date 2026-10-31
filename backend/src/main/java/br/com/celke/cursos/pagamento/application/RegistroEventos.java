package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.pagamento.domain.EventoPagamento;
import br.com.celke.cursos.pagamento.domain.StatusEvento;
import br.com.celke.cursos.pagamento.infra.EventosPagamentoJdbc;
import br.com.celke.cursos.pagamento.infra.EventosPagamentoJdbc.NovoEvento;
import br.com.celke.cursos.shared.fila.FilaService;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Grava um evento normalizado e o coloca na fila, na mesma transação: ou os dois ficam, ou nenhum.
 * Mesmo id externo do mesmo gateway é gravado uma vez só (o reenvio vira "duplicado"). Usado pelo
 * webhook e pela reconciliação.
 */
@Component
public class RegistroEventos {

    public static final String TIPO_JOB = "pagamento.processar-evento";

    private final EventosPagamentoJdbc eventos;
    private final FilaService fila;
    private final JsonMapper jsonMapper;

    public RegistroEventos(EventosPagamentoJdbc eventos, FilaService fila, JsonMapper jsonMapper) {
        this.eventos = eventos;
        this.fila = fila;
        this.jsonMapper = jsonMapper;
    }

    /** Devolve o id do evento gravado; vazio se já existia. */
    @Transactional
    public Optional<Long> registrar(Long gatewayId, EventoPagamento evento, String payload, Instant agora) {
        Optional<Long> id = eventos.inserir(new NovoEvento(
                gatewayId,
                evento.idExterno(),
                evento.tipoOriginal(),
                agora,
                evento.ocorridoEm(),
                evento.transacao(),
                payload,
                jsonMapper.writeValueAsString(evento),
                StatusEvento.RECEBIDO.name(),
                null));
        id.ifPresent(eventoId -> enfileirar(eventoId, agora));
        return id;
    }

    /** Notificação autenticada que não foi interpretada: fica em ERRO, com o corpo cru, para o admin ver. */
    @Transactional
    public Optional<Long> registrarInvalido(
            Long gatewayId, String idExterno, String payload, String erro, Instant agora) {
        return eventos.inserir(new NovoEvento(
                gatewayId, idExterno, "INVALIDO", agora, null, null, payload, null, StatusEvento.ERRO.name(), erro));
    }

    public void enfileirar(Long eventoId, Instant agora) {
        fila.enfileirar(TIPO_JOB, new PayloadEvento(eventoId), agora);
    }

    /** Payload do job que processa um evento. */
    public record PayloadEvento(Long eventoId) {}
}
