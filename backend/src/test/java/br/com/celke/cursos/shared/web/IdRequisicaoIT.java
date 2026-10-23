package br.com.celke.cursos.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Cobre: o cabeçalho X-Request-Id em toda resposta, inclusive as barradas pela segurança.
 * Não cobre: regras de formato do id (IdRequisicaoTest).
 * Dado criado: nenhum.
 */
class IdRequisicaoIT extends IntegrationTest {

    @Test
    @DisplayName("devolve o id recebido quando ele é válido")
    void devolveIdRecebido() {
        assertThat(mvc.get().uri("/actuator/health").header("X-Request-Id", "abc-123"))
                .hasStatusOk()
                .hasHeader("X-Request-Id", "abc-123");
    }

    @Test
    @DisplayName("gera um id quando a requisição não traz um")
    void geraId() {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk().headers().containsHeader("X-Request-Id");
    }

    @Test
    @DisplayName("troca um id com caracteres inseguros por um gerado")
    void trocaIdInseguro() {
        var resposta = mvc.get()
                .uri("/actuator/health")
                .header("X-Request-Id", "id com espaço\r\nX-Falso: 1")
                .exchange();
        assertThat(resposta.getResponse().getHeader("X-Request-Id"))
                .isNotBlank()
                .doesNotContain(" ", "\n", "X-Falso");
    }

    @Test
    @DisplayName("resposta 401 da segurança também traz o id")
    void idNoBloqueioDaSeguranca() {
        assertThat(mvc.get().uri("/api/qualquer").header("X-Request-Id", "id-bloqueado"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasHeader("X-Request-Id", "id-bloqueado");
    }
}
