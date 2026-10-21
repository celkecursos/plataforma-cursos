package br.com.celke.cursos.auth.api;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.auth.application.AuthProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

class VerificadorOrigemTest {

    private final VerificadorOrigem verificador = new VerificadorOrigem(new AuthProperties(
            "segredo",
            "plataforma-cursos",
            Duration.ofMinutes(10),
            Duration.ofDays(14),
            true,
            List.of("https://aluno.celke.uno", "https://ADM.celke.uno/ ")));

    private static MockHttpServletRequest requisicao(String origin, String secFetchSite) {
        var request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        if (secFetchSite != null) {
            request.addHeader("Sec-Fetch-Site", secFetchSite);
        }
        return request;
    }

    @ParameterizedTest(name = "[{0}] [{1}]")
    @CsvSource({
        "https://aluno.celke.uno,  same-origin",
        "https://ALUNO.celke.uno/, ",
        "https://adm.celke.uno,    same-site",
        ",                          ",
        ",                          same-origin",
        ",                          none",
    })
    @DisplayName("aceita origem da lista (sem diferenciar maiúsculas e barra final) e requisição sem Origin")
    void aceita(String origin, String secFetchSite) {
        assertThatCode(() -> verificador.verificar(requisicao(origin, secFetchSite)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(
            strings = {
                "https://outra-app.celke.uno",
                "http://aluno.celke.uno",
                "https://aluno.celke.uno:8443",
                "https://aluno.celke.uno.site-estranho.com",
                "null"
            })
    @DisplayName("recusa origem fora da lista, inclusive outro subdomínio do mesmo site e Origin null")
    void recusaOrigem(String origin) {
        assertThatThrownBy(() -> verificador.verificar(requisicao(origin, null)))
                .isInstanceOf(OrigemNaoPermitidaException.class)
                .hasMessage("Origem não permitida.");
    }

    @Test
    @DisplayName("recusa requisição marcada pelo navegador como cross-site, mesmo sem Origin")
    void recusaCrossSite() {
        assertThatThrownBy(() -> verificador.verificar(requisicao(null, "cross-site")))
                .isInstanceOf(OrigemNaoPermitidaException.class);
    }

    @Test
    @DisplayName("sem origens configuradas, toda requisição de navegador é recusada")
    void semOrigensConfiguradas() {
        var semLista = new VerificadorOrigem(new AuthProperties(
                "segredo", "plataforma-cursos", Duration.ofMinutes(10), Duration.ofDays(14), true, List.of()));

        assertThatThrownBy(() -> semLista.verificar(requisicao("https://aluno.celke.uno", null)))
                .isInstanceOf(OrigemNaoPermitidaException.class);
    }
}
