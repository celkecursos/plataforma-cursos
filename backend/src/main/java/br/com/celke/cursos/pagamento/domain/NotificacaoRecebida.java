package br.com.celke.cursos.pagamento.domain;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Requisição crua de uma notificação (webhook): cabeçalhos com o nome em minúsculas e o corpo como
 * chegou. Cada meio de pagamento autentica e interpreta do seu jeito.
 */
public record NotificacaoRecebida(Map<String, String> cabecalhos, byte[] corpo) {

    public NotificacaoRecebida {
        cabecalhos = cabecalhos.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(e -> e.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue));
        corpo = corpo.clone();
    }

    public String cabecalho(String nome) {
        return cabecalhos.get(nome.toLowerCase(Locale.ROOT));
    }

    public String corpoTexto() {
        return new String(corpo, StandardCharsets.UTF_8);
    }
}
