package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.pagamento.domain.EventoRecebido;
import br.com.celke.cursos.pagamento.domain.StatusEvento;
import br.com.celke.cursos.pagamento.infra.EventoRecebidoRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Eventos recebidos no painel: lista por status, detalhe com o corpo e o reprocessamento. */
@Service
public class EventoAdmService {

    private final EventoRecebidoRepository eventos;
    private final RegistroEventos registro;
    private final Clock clock;

    public EventoAdmService(EventoRecebidoRepository eventos, RegistroEventos registro, Clock clock) {
        this.eventos = eventos;
        this.registro = registro;
        this.clock = clock;
    }

    /** Mais recentes primeiro; sem status, todos. */
    @Transactional(readOnly = true)
    public Page<EventoRecebido> listar(StatusEvento status, Pageable pagina) {
        PageRequest ordenada =
                PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(), Sort.by(Sort.Direction.DESC, "id"));
        return status == null ? eventos.findAll(ordenada) : eventos.findByStatus(status, ordenada);
    }

    @Transactional(readOnly = true)
    public EventoRecebido buscar(Long id) {
        return eventos.findById(id).orElseThrow(() -> new NaoEncontradoException("Evento não encontrado."));
    }

    /**
     * Coloca de novo na fila um evento em ERRO ou IGNORADO (ex.: a oferta foi cadastrada depois). Sem o
     * evento normalizado (corpo que não foi interpretado, ou apagado pela retenção), não há o que refazer.
     */
    @Transactional
    public EventoRecebido reprocessar(Long id) {
        EventoRecebido evento =
                eventos.travar(id).orElseThrow(() -> new NaoEncontradoException("Evento não encontrado."));
        if (evento.getStatus() != StatusEvento.ERRO && evento.getStatus() != StatusEvento.IGNORADO) {
            throw new ConflitoException("Só eventos com erro ou ignorados podem ser reprocessados.");
        }
        if (evento.getDados() == null) {
            throw new ConflitoException(
                    "Este evento não foi interpretado ou o corpo já foi apagado pela retenção: reprocessar não resolve.");
        }
        evento.reabrir();
        registro.enfileirar(evento.getId(), clock.instant());
        return evento;
    }
}
