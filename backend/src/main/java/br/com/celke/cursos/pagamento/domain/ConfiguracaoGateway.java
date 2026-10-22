package br.com.celke.cursos.pagamento.domain;

import java.util.Map;

/**
 * Configuração de um gateway já decifrada, entregue à implementação dele: campos comuns e secretos
 * juntos, pela chave do {@link CampoConfiguracao}.
 */
public record ConfiguracaoGateway(Long gatewayId, TipoGateway tipo, Map<String, String> valores) {

    public ConfiguracaoGateway {
        valores = Map.copyOf(valores);
    }

    /** Valor do campo; nulo se não preenchido. */
    public String valor(String chave) {
        String valor = valores.get(chave);
        return valor == null || valor.isBlank() ? null : valor;
    }

    /** Valor do campo ou o padrão informado. */
    public String valor(String chave, String padrao) {
        String valor = valor(chave);
        return valor == null ? padrao : valor;
    }
}
