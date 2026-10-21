package br.com.celke.cursos.shared.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.shared.fila.ErroPermanenteException;
import br.com.celke.cursos.shared.fila.Job;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class EnvioEmailManipuladorTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    private EnvioEmailManipulador manipulador;

    @BeforeEach
    void criar() {
        manipulador = new EnvioEmailManipulador(
                mailSender,
                templateEngine,
                jsonMapper,
                new EmailProperties("nao-responda@celke.uno", "Plataforma de Cursos"));
    }

    private Job job(MensagemEmail mensagem) {
        return new Job(1, "EMAIL", jsonMapper.writeValueAsString(mensagem), 1, 5);
    }

    @Test
    @DisplayName("monta o HTML do template com as variáveis e envia com remetente e assunto")
    void envia() throws Exception {
        when(templateEngine.process(eq("email/teste"), any(IContext.class))).thenReturn("<p>Olá, Ana</p>");
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        manipulador.processar(job(new MensagemEmail("ana@teste.celke.uno", "Assunto", "teste", Map.of("nome", "Ana"))));

        ArgumentCaptor<IContext> contexto = ArgumentCaptor.forClass(IContext.class);
        verify(templateEngine).process(eq("email/teste"), contexto.capture());
        assertThat(contexto.getValue().getVariable("nome")).isEqualTo("Ana");

        ArgumentCaptor<MimeMessage> enviado = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(enviado.capture());
        MimeMessage mensagem = enviado.getValue();
        assertThat(mensagem.getSubject()).isEqualTo("Assunto");
        assertThat(((InternetAddress) mensagem.getFrom()[0]).getAddress()).isEqualTo("nao-responda@celke.uno");
        assertThat(((InternetAddress) mensagem.getFrom()[0]).getPersonal()).isEqualTo("Plataforma de Cursos");
        assertThat(mensagem.getAllRecipients()).extracting(Object::toString).containsExactly("ana@teste.celke.uno");
    }

    @Test
    @DisplayName("aceita mensagem sem variáveis")
    void semVariaveis() throws Exception {
        when(templateEngine.process(eq("email/teste"), any(IContext.class))).thenReturn("<p>Olá</p>");
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        manipulador.processar(job(new MensagemEmail("ana@teste.celke.uno", "Assunto", "teste", null)));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("template fora do padrão é erro permanente e nada é enviado")
    void templateInvalido() {
        Job job = job(new MensagemEmail("ana@teste.celke.uno", "Assunto", "../segredo", Map.of()));

        assertThatThrownBy(() -> manipulador.processar(job))
                .isInstanceOf(ErroPermanenteException.class)
                .hasMessage("Nome de template inválido.");
        verify(templateEngine, never()).process(anyString(), any(IContext.class));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("tipo do job é EMAIL")
    void tipo() {
        assertThat(manipulador.tipo()).isEqualTo("EMAIL");
    }
}
