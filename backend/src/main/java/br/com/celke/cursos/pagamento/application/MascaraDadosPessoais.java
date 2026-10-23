package br.com.celke.cursos.pagamento.application;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Mascara documento, telefone e IP no corpo de uma notificação antes de gravar: o painel mostra o
 * corpo para conferência, sem guardar dado pessoal que a plataforma não usa. Vale para qualquer meio
 * de pagamento: o campo é reconhecido pelo nome.
 */
public final class MascaraDadosPessoais {

    /** Nomes de campo (sem diferenciar maiúsculas) cujo valor é mascarado. */
    private static final Pattern CAMPO_PESSOAL =
            Pattern.compile(".*(document|doc|cpf|cnpj|phone|telefone|celular|_ip|^ip).*");

    private MascaraDadosPessoais() {}

    /** Mascara o JSON no lugar (objetos e listas, em qualquer profundidade). */
    public static JsonNode mascarar(JsonNode no) {
        if (no instanceof ObjectNode objeto) {
            List<Map.Entry<String, JsonNode>> campos = List.copyOf(objeto.properties());
            for (Map.Entry<String, JsonNode> campo : campos) {
                JsonNode valor = campo.getValue();
                if (valor.isValueNode() && !valor.isNull() && pessoal(campo.getKey())) {
                    objeto.put(campo.getKey(), mascarar(valor.asString()));
                } else {
                    mascarar(valor);
                }
            }
        } else if (no instanceof ArrayNode lista) {
            lista.forEach(MascaraDadosPessoais::mascarar);
        }
        return no;
    }

    /** Só os 2 últimos caracteres ficam visíveis ("***64"). */
    public static String mascarar(String valor) {
        if (valor == null || valor.isBlank()) {
            return valor;
        }
        String limpo = valor.strip();
        return limpo.length() <= 2 ? "***" : "***" + limpo.substring(limpo.length() - 2);
    }

    static boolean pessoal(String campo) {
        return CAMPO_PESSOAL.matcher(campo.toLowerCase(Locale.ROOT)).matches();
    }
}
