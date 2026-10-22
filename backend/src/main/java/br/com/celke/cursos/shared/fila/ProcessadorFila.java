package br.com.celke.cursos.shared.fila;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Reserva um job, entrega ao manipulador do tipo e registra o resultado na fila. */
@Component
public class ProcessadorFila {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessadorFila.class);

    private final FilaService fila;
    private final Map<String, ManipuladorJob> manipuladores;
    private final Clock clock;

    public ProcessadorFila(FilaService fila, List<ManipuladorJob> manipuladores, Clock clock) {
        this.fila = fila;
        this.manipuladores =
                manipuladores.stream().collect(Collectors.toUnmodifiableMap(ManipuladorJob::tipo, Function.identity()));
        this.clock = clock;
    }

    /** Processa um job; devolve {@code false} quando não havia job disponível. */
    public boolean processarProximo() {
        var reservado = fila.reservar(manipuladores.keySet(), clock.instant());
        if (reservado.isEmpty()) {
            return false;
        }
        Job job = reservado.get();
        ManipuladorJob manipulador = manipuladores.get(job.tipo());
        if (job.excedeuTentativas()) {
            desistir(manipulador, job, "Tentativas esgotadas (o processamento foi interrompido).");
            return true;
        }
        try {
            manipulador.processar(job);
            fila.concluir(job, clock.instant());
        } catch (ErroPermanenteException e) {
            LOG.error("Job {} ({}) falhou sem nova tentativa: {}", job.id(), job.tipo(), e.getMessage());
            desistir(manipulador, job, e.getMessage());
        } catch (Exception e) {
            LOG.warn("Job {} ({}) falhou na tentativa {}: {}", job.id(), job.tipo(), job.tentativas(), e.toString());
            fila.falhar(job, e.toString(), clock.instant());
            if (job.ultimaTentativa()) {
                avisar(manipulador, job, e.toString());
            }
        }
        return true;
    }

    private void desistir(ManipuladorJob manipulador, Job job, String motivo) {
        fila.falharDefinitivo(job, motivo, clock.instant());
        avisar(manipulador, job, motivo);
    }

    /** O estado da fila já foi gravado: uma falha ao avisar o manipulador só vai para o log. */
    private static void avisar(ManipuladorJob manipulador, Job job, String motivo) {
        try {
            manipulador.desistir(job, motivo);
        } catch (RuntimeException e) {
            LOG.error("Job {} ({}): falha ao registrar a desistência", job.id(), job.tipo(), e);
        }
    }
}
