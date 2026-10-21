package br.com.celke.cursos.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

/**
 * Cobre: rotas públicas de infraestrutura e o bloqueio padrão de qualquer outra rota.
 * Não cobre: autenticação por token (fase de autenticação).
 * Dado criado: nenhum.
 */
class SegurancaPadraoIT extends IntegrationTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    @DisplayName("health e probes são públicos e respondem UP")
    void healthPublico(String rota) {
        assertThat(mvc.get().uri(rota))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("UP");
    }

    @Test
    @DisplayName("health não expõe detalhes dos componentes")
    void healthSemDetalhes() {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk().bodyJson().doesNotHavePath("$.components");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/qualquer-rota", "/actuator/env", "/actuator/beans", "/actuator/metrics"})
    @DisplayName("qualquer outra rota exige autenticação")
    void rotaPrivadaSemToken(String rota) {
        assertThat(mvc.get().uri(rota)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("POST em rota pública só de leitura também exige autenticação")
    void metodoNaoDeclaradoExigeAutenticacao() {
        assertThat(mvc.post().uri("/actuator/health")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/v3/api-docs", "/swagger-ui.html"})
    @DisplayName("documentação desligada por padrão")
    void documentacaoDesligada(String rota) {
        assertThat(mvc.get().uri(rota)).hasStatus(HttpStatus.NOT_FOUND);
    }
}
