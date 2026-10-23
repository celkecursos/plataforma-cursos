package br.com.celke.cursos.auth.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Aplica a retenção da auditoria de login uma vez por dia. Só no worker, que tem o agendamento ligado. */
@Component
@ConditionalOnBooleanProperty("app.fila.consumidor-ativo")
public class LimpezaAuditoriaLogin {

    private final AuditoriaLoginService auditoriaLoginService;

    public LimpezaAuditoriaLogin(AuditoriaLoginService auditoriaLoginService) {
        this.auditoriaLoginService = auditoriaLoginService;
    }

    @Scheduled(cron = "0 40 4 * * *", zone = "America/Sao_Paulo")
    public void limpar() {
        auditoriaLoginService.apagarVencidas();
    }
}
