package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.pagamento.application.RegistroEventos.PayloadEvento;
import br.com.celke.cursos.shared.fila.Job;
import br.com.celke.cursos.shared.fila.ManipuladorJob;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Job {@code pagamento.processar-evento}: aplica um evento gravado pelo webhook ou pela reconciliação. */
@Component
public class ProcessarEventoManipulador implements ManipuladorJob {

    private final ProcessamentoEventoService processamento;
    private final JsonMapper jsonMapper;

    public ProcessarEventoManipulador(ProcessamentoEventoService processamento, JsonMapper jsonMapper) {
        this.processamento = processamento;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String tipo() {
        return RegistroEventos.TIPO_JOB;
    }

    @Override
    public void processar(Job job) {
        processamento.processar(payload(job).eventoId());
    }

    @Override
    public void desistir(Job job, String motivo) {
        processamento.falhar(payload(job).eventoId(), motivo);
    }

    private PayloadEvento payload(Job job) {
        return jsonMapper.readValue(job.payload(), PayloadEvento.class);
    }
}
