package br.com.celke.cursos.forum.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TopicoTest {

    private static final Instant T0 = Instant.parse("2026-03-10T12:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);

    @Test
    @DisplayName("tópico novo: texto aparado, HTML gerado e filtrado, visível, não fixado")
    void novo() {
        Topico topico = new Topico(5L, 9L, 7L, "  Dúvida  ", "  **Ajuda** <script>x</script> ", T0);

        assertThat(topico.getTitulo()).isEqualTo("Dúvida");
        assertThat(topico.getCorpoMarkdown()).isEqualTo("**Ajuda** <script>x</script>");
        assertThat(topico.getCorpoHtml()).contains("<strong>Ajuda</strong>").doesNotContain("<script");
        assertThat(topico.isOculto()).isFalse();
        assertThat(topico.isFixado()).isFalse();
        assertThat(topico.isRespondidoInstrutor()).isFalse();
        assertThat(topico.getCriadoEm()).isEqualTo(T0);
    }

    @Test
    @DisplayName("moderação muda só os campos enviados")
    void moderar() {
        Topico topico = new Topico(5L, null, 7L, "T", "C", T0);

        topico.moderar(null, true, T1);
        assertThat(topico.isFixado()).isTrue();
        assertThat(topico.isOculto()).isFalse();

        topico.moderar(true, null, T1);
        assertThat(topico.isFixado()).isTrue();
        assertThat(topico.isOculto()).isTrue();
        assertThat(topico.getAtualizadoEm()).isEqualTo(T1);
    }

    @Test
    @DisplayName("resposta do instrutor marca o tópico como respondido")
    void respondido() {
        Topico topico = new Topico(5L, null, 7L, "T", "C", T0);

        topico.registrarRespostaInstrutor(T1);

        assertThat(topico.isRespondidoInstrutor()).isTrue();
    }

    @Test
    @DisplayName("resposta: HTML filtrado, instrutor e moderação")
    void resposta() {
        Resposta resposta = new Resposta(1L, 2L, " [x](javascript:alert(1)) ", true, T0);

        assertThat(resposta.getCorpoHtml()).doesNotContain("javascript:");
        assertThat(resposta.isInstrutor()).isTrue();
        resposta.moderar(true, T1);
        assertThat(resposta.isOculto()).isTrue();
    }
}
