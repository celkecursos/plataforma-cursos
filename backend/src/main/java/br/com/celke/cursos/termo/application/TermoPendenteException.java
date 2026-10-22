package br.com.celke.cursos.termo.application;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import br.com.celke.cursos.termo.domain.Termo;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Há termo vigente sem aceite: 403 com a lista {@code termosPendentes} ({@code id}, {@code tipo},
 * {@code versao}) para o front pedir o aceite.
 */
public class TermoPendenteException extends RegraNegocioException {

    private final transient List<Map<String, Object>> pendentes;

    public TermoPendenteException(List<Termo> termos) {
        super(HttpStatus.FORBIDDEN, "Aceite os termos atualizados para continuar.");
        this.pendentes = termos.stream()
                .map(t -> Map.<String, Object>of("id", t.getId(), "tipo", t.getTipo(), "versao", t.getVersao()))
                .toList();
    }

    @Override
    public Map<String, Object> propriedades() {
        return Map.of("termosPendentes", pendentes);
    }
}
