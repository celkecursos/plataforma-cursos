package br.com.celke.cursos.pagamento.domain;

import java.util.List;

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
        List<String> opcoes) {

    public CampoConfiguracao {
        opcoes = opcoes == null ? List.of() : List.copyOf(opcoes);
    }

    public static CampoConfiguracao texto(String chave, String rotulo, boolean obrigatorio, String dica) {
        return new CampoConfiguracao(chave, rotulo, TipoCampo.TEXTO, false, obrigatorio, dica, null, List.of());
    }

    public static CampoConfiguracao segredo(String chave, String rotulo, boolean obrigatorio, String dica) {
        return new CampoConfiguracao(chave, rotulo, TipoCampo.TEXTO, true, obrigatorio, dica, null, List.of());
    }
}
