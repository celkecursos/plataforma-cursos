package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.auth.application.TokenSeguro;
import br.com.celke.cursos.pagamento.domain.EventoPagamento;
import br.com.celke.cursos.pagamento.domain.Gateway;
import br.com.celke.cursos.pagamento.domain.GatewayPagamento;
import br.com.celke.cursos.pagamento.domain.NotificacaoInvalidaException;
import br.com.celke.cursos.pagamento.domain.NotificacaoRecebida;
import br.com.celke.cursos.pagamento.infra.GatewayRepository;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Recebe a notificação de um meio de pagamento: identifica o gateway pelo token da URL, autentica,
 * interpreta, grava e enfileira, respondendo na hora. O processamento fica com o worker. Só o que não
 * pôde ser gravado vira erro para o meio de pagamento: ele reenvia (e desativa a URL se continuar
 * falhando), então evento válido que não se aplica é gravado e respondido com sucesso.
 */
@Service
public class WebhookService {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookService.class);

    private final GatewayRepository gateways;
    private final GatewaysRegistrados registrados;
    private final ConfiguracaoGatewayService configuracoes;
    private final RegistroEventos registro;
    private final AvisosPagamento avisos;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public WebhookService(
            GatewayRepository gateways,
            GatewaysRegistrados registrados,
            ConfiguracaoGatewayService configuracoes,
            RegistroEventos registro,
            AvisosPagamento avisos,
            JsonMapper jsonMapper,
            Clock clock) {
        this.gateways = gateways;
        this.registrados = registrados;
        this.configuracoes = configuracoes;
        this.registro = registro;
        this.avisos = avisos;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional
    public ResultadoWebhook receber(String webhookToken, NotificacaoRecebida notificacao) {
        Gateway gateway = gateways.findByWebhookToken(webhookToken)
                .orElseThrow(() -> new NaoEncontradoException("Endereço de notificação desconhecido."));
        GatewayPagamento implementacao = registrados.de(gateway.getTipo());
        if (!implementacao.autenticar(notificacao, configuracoes.ler(gateway))) {
            LOG.warn("Notificação recusada para o gateway {}: autenticação inválida", gateway.getId());
            throw new RegraNegocioException(HttpStatus.UNAUTHORIZED, "Notificação não autenticada.");
        }
        Instant agora = clock.instant();
        String payload = payloadMascarado(notificacao.corpoTexto());
        List<EventoPagamento> eventos;
        try {
            eventos = implementacao.interpretar(notificacao);
        } catch (NotificacaoInvalidaException e) {
            String idExterno = "invalido:" + TokenSeguro.hash(notificacao.corpoTexto());
            Optional<Long> id = registro.registrarInvalido(gateway.getId(), idExterno, payload, e.getMessage(), agora);
            id.ifPresent(eventoId -> avisos.eventoComErro(eventoId, "INVALIDO", e.getMessage()));
            return id.isPresent() ? ResultadoWebhook.ERRO : ResultadoWebhook.DUPLICADO;
        }
        boolean algumNovo = false;
        for (EventoPagamento evento : eventos) {
            algumNovo |=
                    registro.registrar(gateway.getId(), evento, payload, agora).isPresent();
        }
        return algumNovo ? ResultadoWebhook.RECEBIDO : ResultadoWebhook.DUPLICADO;
    }

    /** Corpo em JSON com os dados pessoais mascarados; corpo que não é JSON vai como texto (string JSON). */
    String payloadMascarado(String corpo) {
        try {
            JsonNode arvore = jsonMapper.readTree(corpo);
            if (arvore != null && (arvore.isObject() || arvore.isArray())) {
                return jsonMapper.writeValueAsString(MascaraDadosPessoais.mascarar(arvore));
            }
        } catch (JacksonException e) {
            // não é JSON: guarda como texto
        }
        return jsonMapper.writeValueAsString(corpo);
    }

    public enum ResultadoWebhook {
        /** Gravado e na fila. */
        RECEBIDO,
        /** Já tinha chegado (reenvio): nada muda. */
        DUPLICADO,
        /** Gravado como erro (corpo não interpretado): reenviar não resolve. */
        ERRO
    }
}
