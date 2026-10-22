package br.com.celke.cursos.arquivo.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class FormatoArquivoTest {

    private static byte[] texto(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"application/zip, ZIP", "application/pdf, PDF", "text/plain, TEXTO"})
    @DisplayName("reconhece os tipos aceitos")
    void tiposAceitos(String contentType, FormatoArquivo esperado) {
        assertThat(FormatoArquivo.doContentType(contentType)).contains(esperado);
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/x-msdownload", "text/html", "image/svg+xml", "APPLICATION/ZIP", ""})
    @DisplayName("recusa os outros tipos (executável, HTML, SVG...)")
    void tiposRecusados(String contentType) {
        assertThat(FormatoArquivo.doContentType(contentType)).isEmpty();
    }

    @Test
    @DisplayName("ZIP: assinatura de arquivo local ou de ZIP vazio")
    void zip() {
        assertThat(FormatoArquivo.ZIP.conteudoConfere(new byte[] {'P', 'K', 3, 4, 20, 0}))
                .isTrue();
        assertThat(FormatoArquivo.ZIP.conteudoConfere(new byte[] {'P', 'K', 5, 6}))
                .isTrue();
        assertThat(FormatoArquivo.ZIP.conteudoConfere(texto("MZ\u0090executável")))
                .isFalse();
        assertThat(FormatoArquivo.ZIP.conteudoConfere(new byte[] {'P', 'K'})).isFalse();
    }

    @Test
    @DisplayName("PDF: começa com %PDF-")
    void pdf() {
        assertThat(FormatoArquivo.PDF.conteudoConfere(texto("%PDF-1.7\n"))).isTrue();
        assertThat(FormatoArquivo.PDF.conteudoConfere(texto("<html><script>"))).isFalse();
        assertThat(FormatoArquivo.PDF.conteudoConfere(texto("%PDF"))).isFalse();
    }

    @Test
    @DisplayName("texto: sem byte nulo no início; vazio não passa")
    void textoPuro() {
        assertThat(FormatoArquivo.TEXTO.conteudoConfere(texto("public class Ola {}\n// acentuação")))
                .isTrue();
        assertThat(FormatoArquivo.TEXTO.conteudoConfere(new byte[] {'P', 'K', 3, 4, 0, 0}))
                .isFalse();
        assertThat(FormatoArquivo.TEXTO.conteudoConfere(new byte[0])).isFalse();
    }
}
