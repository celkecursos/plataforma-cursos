package br.com.celke.cursos.shared.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

class ContextoAuditoriaTest {

    @AfterEach
    void limpar() {
        MDC.clear();
    }

    @Test
    @DisplayName("rota com as variáveis quando o Spring MVC já escolheu o mapeamento; senão o caminho")
    void rota() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/adm/cursos/7");
        assertThat(ContextoAuditoria.rota(request)).isEqualTo("PUT /api/adm/cursos/7");

        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/adm/cursos/{id}");
        assertThat(ContextoAuditoria.rota(request)).isEqualTo("PUT /api/adm/cursos/{id}");

        MockHttpServletRequest longa = new MockHttpServletRequest("GET", "/" + "a".repeat(300));
        assertThat(ContextoAuditoria.rota(longa)).hasSize(200);
    }

    @Test
    @DisplayName("sem requisição: sistema; com o job da fila marcado, o job")
    void foraDaRequisicao() {
        assertThat(ContextoAuditoria.atual().origem()).isEqualTo("sistema");
        assertThat(ContextoAuditoria.atual().usuarioId()).isNull();

        MDC.put(ContextoAuditoria.CHAVE_JOB, "pagamento.processar-evento");
        assertThat(ContextoAuditoria.atual().origem()).isEqualTo("job pagamento.processar-evento");
        assertThat(ContextoAuditoria.atual().ip()).isNull();
    }
}
