package br.com.celke.cursos.shared.email;

import br.com.celke.cursos.shared.fila.ErroPermanenteException;
import br.com.celke.cursos.shared.fila.Job;
import br.com.celke.cursos.shared.fila.ManipuladorJob;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import tools.jackson.databind.json.JsonMapper;

/** Monta o HTML do template com Thymeleaf e envia pelo SMTP configurado. Roda no worker. */
@Component
public class EnvioEmailManipulador implements ManipuladorJob {

    private static final Locale PT_BR = Locale.of("pt", "BR");

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final JsonMapper jsonMapper;
    private final EmailProperties props;

    public EnvioEmailManipulador(
            JavaMailSender mailSender, TemplateEngine templateEngine, JsonMapper jsonMapper, EmailProperties props) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.jsonMapper = jsonMapper;
        this.props = props;
    }

    @Override
    public String tipo() {
        return EmailService.TIPO_JOB;
    }

    @Override
    public void processar(Job job) throws Exception {
        MensagemEmail mensagem = jsonMapper.readValue(job.payload(), MensagemEmail.class);
        if (mensagem.template() == null
                || !EmailService.NOME_TEMPLATE.matcher(mensagem.template()).matches()) {
            throw new ErroPermanenteException("Nome de template inválido.");
        }
        Context contexto = new Context(PT_BR);
        if (mensagem.variaveis() != null) {
            mensagem.variaveis().forEach(contexto::setVariable);
        }
        String html = templateEngine.process("email/" + mensagem.template(), contexto);

        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());
        helper.setFrom(props.remetente(), props.nomeRemetente());
        helper.setTo(mensagem.para());
        helper.setSubject(mensagem.assunto());
        helper.setText(html, true);
        mailSender.send(mime);
    }
}
