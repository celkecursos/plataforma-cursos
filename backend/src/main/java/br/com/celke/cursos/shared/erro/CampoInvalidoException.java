package br.com.celke.cursos.shared.erro;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Um campo recusado por uma regra que a validação do DTO não alcança (ex.: senha comum): 400 com a
 * mensagem no {@code detail} e na lista {@code erros}, no mesmo formato da validação.
 */
public class CampoInvalidoException extends RegraNegocioException {

    private final ErroCampo erro;

    public CampoInvalidoException(String campo, String mensagem) {
        super(HttpStatus.BAD_REQUEST, mensagem);
        this.erro = new ErroCampo(campo, mensagem);
    }

    @Override
    public Map<String, Object> propriedades() {
        return Map.of("erros", List.of(erro));
    }
}
