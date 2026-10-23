package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.shared.erro.ErroCampo;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Campos da configuração que não passam pelas regras do meio de pagamento: 400 com a lista {@code erros}. */
public class ConfiguracaoInvalidaException extends RegraNegocioException {

    private final List<ErroCampo> erros;

    public ConfiguracaoInvalidaException(List<ErroCampo> erros) {
        super(HttpStatus.BAD_REQUEST, "Dados inválidos.");
        this.erros = List.copyOf(erros);
    }

    public List<ErroCampo> getErros() {
        return erros;
    }

    @Override
    public Map<String, Object> propriedades() {
        return Map.of("erros", erros);
    }
}
