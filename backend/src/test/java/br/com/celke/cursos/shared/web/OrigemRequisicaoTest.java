package br.com.celke.cursos.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class OrigemRequisicaoTest {

    @Test
    @DisplayName("lê o IP e o user-agent da requisição")
    void daRequisicao() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("User-Agent", "Navegador/1.0");

        assertThat(OrigemRequisicao.de(request)).isEqualTo(new OrigemRequisicao("203.0.113.7", "Navegador/1.0"));
    }

    @Test
    @DisplayName("corta o user-agent em 255 caracteres e aceita a ausência dele")
    void userAgent() {
        assertThat(new OrigemRequisicao("ip", "a".repeat(255)).userAgent()).hasSize(255);
        assertThat(new OrigemRequisicao("ip", "a".repeat(256)).userAgent()).hasSize(255);
        assertThat(new OrigemRequisicao("ip", null).userAgent()).isNull();
    }
}
