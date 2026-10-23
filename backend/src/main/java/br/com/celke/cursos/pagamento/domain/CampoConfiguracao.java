package br.com.celke.cursos.pagamento.domain;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * Campo da configuração de um meio de pagamento. O painel monta o formulário a partir da lista que o
 * gateway declara; o campo secreto é gravado cifrado e nunca volta nas respostas.
 *
 * @param padrao valor usado quando o campo vem em branco (nulo: sem padrão)
 * @param opcoes valores aceitos num campo {@link TipoCampo#SELECAO}; vazia nos outros
 */
public record CampoConfiguracao(
        String chave,
        String rotulo,
        TipoCampo tipo,
        boolean secreto,
        boolean obrigatorio,
        String dica,
        String padrao,
        List<String> opcoes,
        List<String> hostsPermitidos) {

    public CampoConfiguracao {
        opcoes = opcoes == null ? List.of() : List.copyOf(opcoes);
        hostsPermitidos = hostsPermitidos == null ? List.of() : List.copyOf(hostsPermitidos);
    }

    /** Seleção entre as opções, com um valor padrão. */
    public static CampoConfiguracao selecao(
            String chave, String rotulo, String dica, String padrao, List<String> opcoes) {
        return new CampoConfiguracao(chave, rotulo, TipoCampo.SELECAO, false, true, dica, padrao, opcoes, List.of());
    }

    /**
     * Endereço HTTPS. Com {@code hostsPermitidos}, só esses domínios são aceitos: um endereço trocado no
     * painel não manda o aluno (com o e-mail dele na URL) para um site de terceiros.
     */
    public static CampoConfiguracao url(
            String chave, String rotulo, String dica, String padrao, List<String> hostsPermitidos) {
        return new CampoConfiguracao(
                chave, rotulo, TipoCampo.URL, false, true, dica, padrao, List.of(), hostsPermitidos);
    }

    public static CampoConfiguracao texto(String chave, String rotulo, boolean obrigatorio, String dica) {
        return new CampoConfiguracao(
                chave, rotulo, TipoCampo.TEXTO, false, obrigatorio, dica, null, List.of(), List.of());
    }

    public static CampoConfiguracao segredo(String chave, String rotulo, boolean obrigatorio, String dica) {
        return new CampoConfiguracao(
                chave, rotulo, TipoCampo.TEXTO, true, obrigatorio, dica, null, List.of(), List.of());
    }

    /** Só HTTPS; com hosts permitidos, só esses domínios. */
    public static String erroUrl(String valor, List<String> hostsPermitidos) {
        URI uri;
        try {
            uri = URI.create(valor);
        } catch (IllegalArgumentException e) {
            return "Informe um endereço começando com https://.";
        }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            return "Informe um endereço começando com https://.";
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!hostsPermitidos.isEmpty() && !hostsPermitidos.contains(host)) {
            return "O endereço precisa ser de " + String.join(" ou ", hostsPermitidos) + ".";
        }
        return null;
    }
}
