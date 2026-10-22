package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.pagamento.domain.CampoConfiguracao;
import br.com.celke.cursos.pagamento.domain.ConfiguracaoGateway;
import br.com.celke.cursos.pagamento.domain.Gateway;
import br.com.celke.cursos.pagamento.domain.TipoCampo;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.ErroCampo;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Configuração de um gateway: valida os campos que o meio de pagamento declara, separa os secretos
 * (cifrados) dos comuns e monta a {@link ConfiguracaoGateway} decifrada para a implementação.
 */
@Service
public class ConfiguracaoGatewayService {

    static final int TAMANHO_MAXIMO_VALOR = 500;
    private static final TypeReference<Map<String, String>> MAPA = new TypeReference<>() {};

    private final GatewaysRegistrados registrados;
    private final CifraSegredos cifra;
    private final JsonMapper jsonMapper;

    public ConfiguracaoGatewayService(GatewaysRegistrados registrados, CifraSegredos cifra, JsonMapper jsonMapper) {
        this.registrados = registrados;
        this.cifra = cifra;
        this.jsonMapper = jsonMapper;
    }

    /** Campos comuns e secretos decifrados, juntos. */
    public ConfiguracaoGateway ler(Gateway gateway) {
        Map<String, String> valores = new HashMap<>(comuns(gateway));
        valores.putAll(segredos(gateway));
        return new ConfiguracaoGateway(gateway.getId(), gateway.getTipo(), valores);
    }

    public Map<String, String> comuns(Gateway gateway) {
        return jsonMapper.readValue(gateway.getConfiguracao(), MAPA);
    }

    /** @throws IllegalStateException se a chave não está definida ou não decifra (chave trocada) */
    public Map<String, String> segredos(Gateway gateway) {
        byte[] blob = gateway.getSegredos();
        if (blob == null) {
            return Map.of();
        }
        return jsonMapper.readValue(cifra.decifrar(blob, gateway.getWebhookToken()), MAPA);
    }

    /**
     * Valida e grava a configuração. Campo secreto em branco mantém o valor atual (o painel nunca o
     * recebe de volta); campo comum em branco fica com o padrão do campo, se houver.
     */
    public void configurar(Gateway gateway, String nome, Map<String, String> recebidos, Instant agora) {
        List<CampoConfiguracao> campos = registrados.de(gateway.getTipo()).camposConfiguracao();
        Map<String, String> valores = recebidos == null ? Map.of() : recebidos;
        Map<String, String> segredosAtuais = gateway.getSegredos() == null ? Map.of() : segredos(gateway);
        List<ErroCampo> erros = new ArrayList<>();
        Set<String> chaves = campos.stream().map(CampoConfiguracao::chave).collect(Collectors.toSet());
        valores.keySet().stream()
                .filter(chave -> !chaves.contains(chave))
                .sorted()
                .forEach(chave -> erros.add(new ErroCampo("valores." + chave, "Campo desconhecido.")));

        Map<String, String> comuns = new TreeMap<>();
        Map<String, String> secretos = new TreeMap<>();
        for (CampoConfiguracao campo : campos) {
            String valor = limpo(valores.get(campo.chave()));
            if (campo.secreto() && valor == null) {
                valor = segredosAtuais.get(campo.chave());
            }
            if (valor == null) {
                valor = campo.padrao();
            }
            String erro = erro(campo, valor);
            if (erro != null) {
                erros.add(new ErroCampo("valores." + campo.chave(), erro));
            } else if (valor != null) {
                (campo.secreto() ? secretos : comuns).put(campo.chave(), valor);
            }
        }
        if (!erros.isEmpty()) {
            erros.sort(Comparator.comparing(ErroCampo::campo));
            throw new ConfiguracaoInvalidaException(erros);
        }
        byte[] blob = null;
        if (!secretos.isEmpty()) {
            if (!cifra.disponivel()) {
                throw new ConflitoException(
                        "Defina a PAGAMENTOS_CHAVE no .env do ambiente para gravar as credenciais do meio de pagamento.");
            }
            blob = cifra.cifrar(jsonMapper.writeValueAsString(secretos), gateway.getWebhookToken());
        }
        gateway.configurar(nome, jsonMapper.writeValueAsString(comuns), blob, agora);
    }

    /** Situação de cada campo secreto, sem o valor: configurado e os 4 últimos caracteres. */
    public Map<String, SegredoResumo> resumoSegredos(Gateway gateway) {
        Map<String, String> atuais = gateway.getSegredos() == null ? Map.of() : segredos(gateway);
        Map<String, SegredoResumo> resumo = new LinkedHashMap<>();
        for (CampoConfiguracao campo : registrados.de(gateway.getTipo()).camposConfiguracao()) {
            if (campo.secreto()) {
                String valor = atuais.get(campo.chave());
                resumo.put(
                        campo.chave(),
                        valor == null
                                ? new SegredoResumo(false, null)
                                : new SegredoResumo(
                                        true, valor.length() <= 4 ? null : valor.substring(valor.length() - 4)));
            }
        }
        return resumo;
    }

    /** @param final4 os 4 últimos caracteres (nulo se o segredo é curto demais para mostrar algo) */
    public record SegredoResumo(boolean configurado, String final4) {}

    private static String erro(CampoConfiguracao campo, String valor) {
        if (valor == null) {
            return campo.obrigatorio() ? "Informe " + campo.rotulo() + "." : null;
        }
        if (valor.length() > TAMANHO_MAXIMO_VALOR) {
            return "Use no máximo " + TAMANHO_MAXIMO_VALOR + " caracteres.";
        }
        if (campo.tipo() == TipoCampo.SELECAO && !campo.opcoes().contains(valor)) {
            return "Escolha uma das opções: " + String.join(", ", campo.opcoes()) + ".";
        }
        if (campo.tipo() == TipoCampo.URL && !urlValida(valor)) {
            return "Informe um endereço começando com https://.";
        }
        return null;
    }

    private static boolean urlValida(String valor) {
        try {
            URI uri = URI.create(valor);
            return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String limpo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
