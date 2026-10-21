package br.com.celke.cursos.shared.email;

import br.com.celke.cursos.shared.fila.FilaService;
import java.time.Clock;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Envio de e-mail pela fila: a requisição só grava o job e responde; o worker envia, com novas
 * tentativas se o SMTP falhar.
 */
@Service
@EnableConfigurationProperties(EmailProperties.class)
public class EmailService {

    static final String TIPO_JOB = "EMAIL";

    /** Só letras minúsculas, números e hífen: o nome do template nunca vira um caminho arbitrário. */
    static final Pattern NOME_TEMPLATE = Pattern.compile("[a-z0-9-]+");

    private final FilaService fila;
    private final Clock clock;

    public EmailService(FilaService fila, Clock clock) {
        this.fila = fila;
        this.clock = clock;
    }

    public void enviar(String para, String assunto, String template, Map<String, String> variaveis) {
        if (para == null || para.isBlank() || !para.contains("@")) {
            throw new IllegalArgumentException("Destinatário inválido.");
        }
        if (template == null || !NOME_TEMPLATE.matcher(template).matches()) {
            throw new IllegalArgumentException("Nome de template inválido.");
        }
        fila.enfileirar(TIPO_JOB, new MensagemEmail(para.strip(), assunto, template, variaveis), clock.instant());
    }
}
