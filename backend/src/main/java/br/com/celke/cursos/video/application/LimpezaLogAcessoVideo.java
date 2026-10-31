package br.com.celke.cursos.video.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Aplica a retenção do log de acesso uma vez por dia. Só no worker, que tem o agendamento ligado. */
@Component
@ConditionalOnBooleanProperty("app.fila.consumidor-ativo")
public class LimpezaLogAcessoVideo {

    private final LogAcessoVideoService logAcessoVideoService;

    public LimpezaLogAcessoVideo(LogAcessoVideoService logAcessoVideoService) {
        this.logAcessoVideoService = logAcessoVideoService;
    }

    @Scheduled(cron = "0 30 4 * * *", zone = "America/Sao_Paulo")
    public void limpar() {
        logAcessoVideoService.apagarVencidos();
    }
}
