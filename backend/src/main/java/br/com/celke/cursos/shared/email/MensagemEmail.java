package br.com.celke.cursos.shared.email;

import java.util.Map;

/**
 * Payload do job de e-mail.
 *
 * @param template nome do template em {@code templates/email/} (sem extensão)
 * @param variaveis valores usados no template
 */
public record MensagemEmail(String para, String assunto, String template, Map<String, String> variaveis) {}
