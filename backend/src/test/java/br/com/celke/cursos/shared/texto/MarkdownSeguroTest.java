package br.com.celke.cursos.shared.texto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MarkdownSeguroTest {

    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    @Test
    @DisplayName("Markdown comum vira HTML: ênfase, listas, código, citação e tabela")
    void markdownComum() {
        String html = MarkdownSeguro.paraHtml("""
                Olá **turma**, veja o *exemplo*:

                - item 1
                - item 2

                ```
                int x = 1;
                ```

                > citação
                """);

        assertThat(html)
                .contains("<strong>turma</strong>")
                .contains("<em>exemplo</em>")
                .contains("<li>item 1</li>")
                .contains("<pre><code>int x")
                .contains("<blockquote>");
    }

    @Test
    @DisplayName("link http vira <a> com rel nofollow noopener noreferrer")
    void link() {
        assertThat(MarkdownSeguro.paraHtml("[docs](https://docs.oracle.com)"))
                .contains("href=\"https://docs.oracle.com\"")
                .contains("rel=\"nofollow noopener noreferrer\"");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<script>alert(1)</script>",
                "<img src=x onerror=alert(1)>",
                "<a href=\"javascript:alert(1)\">clique</a>",
                "[clique](javascript:alert(1))",
                "[clique](JaVaScRiPt:alert(1))",
                "<iframe src=\"https://mal.example\"></iframe>",
                "<svg onload=alert(1)>",
                "<p style=\"background:url(javascript:alert(1))\">x</p>",
                "![x](javascript:alert(1))",
                "<div onclick=\"alert(1)\">x</div>",
                "[x](data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==)",
            })
    @DisplayName("XSS: nenhum script, evento, iframe, estilo ou esquema perigoso sobrevive")
    void xss(String ataque) {
        String html = MarkdownSeguro.paraHtml(ataque);

        // O que o usuário escreveu pode aparecer escapado, como texto; o que importa são as tags geradas
        List<String> tags =
                TAG.matcher(html).results().map(m -> m.group().toLowerCase()).toList();
        assertThat(tags)
                .noneMatch(tag -> tag.matches("<(script|img|iframe|svg|object|embed|style)\\b.*"))
                .noneMatch(tag -> tag.matches(".*\\son\\w+\\s*=.*"))
                .noneMatch(tag -> tag.contains("style="))
                .noneMatch(tag -> tag.contains("javascript:"))
                .noneMatch(tag -> tag.contains("data:"));
    }

    @Test
    @DisplayName("HTML escrito pelo usuário aparece como texto, escapado")
    void htmlViraTexto() {
        assertThat(MarkdownSeguro.paraHtml("<b>negrito?</b>")).contains("&lt;b&gt;negrito?&lt;/b&gt;");
    }

    @Test
    @DisplayName("vazio ou nulo vira texto vazio")
    void vazio() {
        assertThat(MarkdownSeguro.paraHtml(null)).isEmpty();
        assertThat(MarkdownSeguro.paraHtml("   ")).isEmpty();
    }
}
