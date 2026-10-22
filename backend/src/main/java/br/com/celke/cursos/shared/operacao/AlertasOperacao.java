package br.com.celke.cursos.shared.operacao;

import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.shared.fila.FilaService;
import br.com.celke.cursos.shared.fila.FilaService.FalhaJob;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Alertas por e-mail enviados pelo worker a cada 15 minutos: jobs que foram para ERRO (uma conversão de
 * vídeo que desistiu, por exemplo) e disco com pouco espaço livre. Falha de envio de e-mail não gera
 * alerta por e-mail (iria pela mesma fila que falhou): fica só no log.
 */
@Component
@ConditionalOnBooleanProperty("app.fila.consumidor-ativo")
@EnableConfigurationProperties(AlertasProperties.class)
public class AlertasOperacao {

    static final Duration INTERVALO = Duration.ofMinutes(15);
    private static final Logger LOG = LoggerFactory.getLogger(AlertasOperacao.class);

    private final FilaService fila;
    private final EmailService emailService;
    private final MedidorDisco medidorDisco;
    private final AlertasProperties props;
    private final Clock clock;

    private Instant ultimaVerificacao;
    private Instant ultimoAlertaDisco;

    public AlertasOperacao(
            FilaService fila,
            EmailService emailService,
            MedidorDisco medidorDisco,
            AlertasProperties props,
            Clock clock) {
        this.fila = fila;
        this.emailService = emailService;
        this.medidorDisco = medidorDisco;
        this.props = props;
        this.clock = clock;
        this.ultimaVerificacao = clock.instant().minus(INTERVALO);
    }

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
    public void verificar() {
        if (!props.ligados()) {
            return;
        }
        Instant agora = clock.instant();
        verificarFalhas(agora);
        verificarDisco(agora);
        ultimaVerificacao = agora;
    }

    private void verificarFalhas(Instant agora) {
        List<FalhaJob> falhas = fila.falhasDesde(ultimaVerificacao, EmailService.TIPO_JOB);
        if (falhas.isEmpty()) {
            return;
        }
        String detalhes = falhas.stream()
                .map(f -> "Job %d (%s): %s".formatted(f.id(), f.tipo(), resumo(f.erro())))
                .collect(Collectors.joining("\n"));
        LOG.warn("{} job(s) foram para ERRO desde {}", falhas.size(), ultimaVerificacao);
        enviar("%d job(s) com erro no worker".formatted(falhas.size()), detalhes);
    }

    private void verificarDisco(Instant agora) {
        MedidorDisco.Espaco espaco = medidorDisco.medir(props.diretorioDisco());
        if (espaco.percentualLivre() >= props.discoMinimoPercentual()) {
            ultimoAlertaDisco = null;
            return;
        }
        if (ultimoAlertaDisco != null
                && ultimoAlertaDisco.plus(props.repeticaoDisco()).isAfter(agora)) {
            return;
        }
        ultimoAlertaDisco = agora;
        LOG.warn("Disco com {}% livre", espaco.percentualLivre());
        enviar(
                "Disco com %d%% livre".formatted(espaco.percentualLivre()),
                "Livre: %d MB de %d MB (alerta abaixo de %d%%)."
                        .formatted(
                                espaco.livre() / (1024 * 1024),
                                espaco.total() / (1024 * 1024),
                                props.discoMinimoPercentual()));
    }

    private void enviar(String titulo, String detalhes) {
        emailService.enviar(
                props.email(),
                "[Plataforma de Cursos] " + titulo,
                "alerta-operacao",
                Map.of("titulo", titulo, "detalhes", detalhes));
    }

    private static String resumo(String erro) {
        if (erro == null) {
            return "(sem mensagem)";
        }
        String linha = erro.strip().replaceAll("\\s+", " ");
        return linha.length() <= 300 ? linha : linha.substring(0, 300) + "…";
    }
}
