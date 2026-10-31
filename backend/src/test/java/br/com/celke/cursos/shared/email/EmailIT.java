package br.com.celke.cursos.shared.email;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.shared.fila.ProcessadorFila;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.Mailpit;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Cobre: e-mail enfileirado, processado pela fila, montado pelo template e entregue ao SMTP real
 * (Mailpit), com as variáveis escapadas no HTML.
 * Não cobre: validação de destinatário e template (EmailServiceTest, EnvioEmailManipuladorTest).
 * Dado criado: e-mail para it-<stamp>@teste.celke.uno.
 */
class EmailIT extends IntegrationTest {

    @Autowired
    private EmailService emailService;

    @Autowired
    private ProcessadorFila processador;

    @Autowired
    private Mailpit mailpit;

    @Test
    @DisplayName("e-mail sai pela fila com remetente, assunto e template preenchido")
    void enviaPelaFila() {
        String destinatario = stamp() + "@teste.celke.uno";

        emailService.enviar(
                destinatario, "Bem-vinda à plataforma", "teste", Map.of("nome", "Ana", "codigo", "<b>123</b>"));
        while (processador.processarProximo()) {
            // processa todos os e-mails pendentes
        }

        Mailpit.EmailRecebido email = mailpit.aguardarEmailPara(destinatario);
        assertThat(email.Subject()).isEqualTo("Bem-vinda à plataforma");
        assertThat(email.From().Address()).isEqualTo("nao-responda@teste.celke.uno");
        assertThat(email.From().Name()).isEqualTo("Plataforma de Cursos");
        assertThat(email.To()).extracting(Mailpit.Endereco::Address).containsExactly(destinatario);
        assertThat(email.HTML())
                .contains("Olá, <span>Ana</span>!")
                // variável com HTML chega escapada: o template não injeta marcação
                .contains("&lt;b&gt;123&lt;/b&gt;")
                .doesNotContain("<b>123</b>");
    }
}
