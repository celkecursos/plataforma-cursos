package br.com.celke.cursos.pagamento.api;

import br.com.celke.cursos.pagamento.api.PagamentoResponses.WebhookResponse;
import br.com.celke.cursos.pagamento.application.PagamentosProperties;
import br.com.celke.cursos.pagamento.application.WebhookService;
import br.com.celke.cursos.pagamento.domain.NotificacaoRecebida;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notificações dos meios de pagamento (rota pública, declarada no {@code SecurityConfig}). O token da
 * URL identifica o gateway; a autenticação é a do próprio meio de pagamento.
 */
@RestController
@RequestMapping("/api/pagamentos/webhooks")
@Tag(name = "Pagamentos — notificações")
public class WebhookController {

    private final WebhookService webhookService;
    private final PagamentosProperties props;

    public WebhookController(WebhookService webhookService, PagamentosProperties props) {
        this.webhookService = webhookService;
        this.props = props;
    }

    @PostMapping("/{webhookToken}")
    @Operation(
            summary = "Receber notificação",
            description = "200 recebido, duplicado ou erro (gravado para o painel); 401 sem autenticação válida;"
                    + " 404 endereço desconhecido; 413 corpo grande demais.")
    public WebhookResponse receber(@PathVariable String webhookToken, HttpServletRequest request) throws IOException {
        if (webhookToken.length() > 64) {
            throw new NaoEncontradoException("Endereço de notificação desconhecido.");
        }
        NotificacaoRecebida notificacao = new NotificacaoRecebida(cabecalhos(request), corpo(request));
        return new WebhookResponse(
                webhookService.receber(webhookToken, notificacao).name().toLowerCase(Locale.ROOT));
    }

    /** Lê o corpo cru até o limite; passou dele, 413 sem ler o resto. */
    private byte[] corpo(HttpServletRequest request) throws IOException {
        long limite = props.webhookTamanhoMaximo().toBytes();
        if (request.getContentLengthLong() > limite) {
            throw grandeDemais();
        }
        try (InputStream entrada = request.getInputStream()) {
            byte[] corpo = entrada.readNBytes((int) limite + 1);
            if (corpo.length > limite) {
                throw grandeDemais();
            }
            return corpo;
        }
    }

    private static Map<String, String> cabecalhos(HttpServletRequest request) {
        Map<String, String> cabecalhos = new HashMap<>();
        for (String nome : Collections.list(request.getHeaderNames())) {
            cabecalhos.put(nome, request.getHeader(nome));
        }
        return cabecalhos;
    }

    private static RegraNegocioException grandeDemais() {
        return new RegraNegocioException(HttpStatus.CONTENT_TOO_LARGE, "Notificação grande demais.");
    }
}
