package br.com.celke.cursos.shared.web;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Formato padrão das listagens paginadas. A página começa em 0; tamanho padrão 20, máximo 100
 * ({@code spring.data.web.pageable}).
 */
public record Pagina<T>(List<T> itens, int pagina, int tamanho, long total, int totalPaginas) {

    public static <T> Pagina<T> de(Page<T> page) {
        return new Pagina<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
