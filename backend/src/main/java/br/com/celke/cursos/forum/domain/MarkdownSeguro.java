package br.com.celke.cursos.forum.domain;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

/**
 * Converte o Markdown do fórum em HTML seguro, em duas camadas: o commonmark escapa todo HTML escrito
 * pelo usuário e descarta links com esquema perigoso ({@code javascript:}); o sanitizador OWASP deixa
 * passar só uma lista fechada de elementos (texto, listas, citações, código, links e tabelas), sem
 * atributos de evento nem estilo. Links ganham {@code rel="nofollow noopener noreferrer"}.
 */
public final class MarkdownSeguro {

    private static final Parser PARSER = Parser.builder().build();

    private static final HtmlRenderer RENDERER =
            HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true).build();

    private static final PolicyFactory POLITICA = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.TABLES)
            .and(new HtmlPolicyBuilder()
                    .allowElements("pre", "code", "hr", "br")
                    .allowElements("a")
                    .allowAttributes("href")
                    .onElements("a")
                    .allowUrlProtocols("http", "https", "mailto")
                    .requireRelsOnLinks("nofollow", "noopener", "noreferrer")
                    .toFactory());

    private MarkdownSeguro() {}

    public static String paraHtml(String markdown) {
        String html = RENDERER.render(PARSER.parse(markdown == null ? "" : markdown));
        return POLITICA.sanitize(html).strip();
    }
}
