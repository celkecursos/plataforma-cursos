package br.com.celke.cursos.auditoria.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retenção da trilha de auditoria, toda semana, só no worker. */
@Component
@ConditionalOnBooleanProperty("app.fila.consumidor-ativo")
public class LimpezaAuditoria {

    private static final Logger LOG = LoggerFactory.getLogger(LimpezaAuditoria.class);

    private final AuditoriaService auditoriaService;

    public LimpezaAuditoria(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @Scheduled(cron = "0 0 5 * * SUN", zone = "America/Sao_Paulo")
    public void limpar() {
        LOG.info("Retenção da auditoria: {} registro(s) apagado(s)", auditoriaService.aplicarRetencao());
    }
}
