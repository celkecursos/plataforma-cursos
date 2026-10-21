package br.com.celke.cursos.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PaginaTest {

    @Test
    @DisplayName("copia itens, página, tamanho, total e total de páginas")
    void deUmaPagina() {
        var page = new PageImpl<>(List.of("c", "d"), PageRequest.of(1, 2), 5);

        assertThat(Pagina.de(page)).isEqualTo(new Pagina<>(List.of("c", "d"), 1, 2, 5, 3));
    }

    @Test
    @DisplayName("página vazia tem total zero e nenhuma página")
    void paginaVazia() {
        var page = new PageImpl<String>(List.of(), PageRequest.of(0, 20), 0);

        assertThat(Pagina.de(page)).isEqualTo(new Pagina<>(List.of(), 0, 20, 0, 0));
    }
}
