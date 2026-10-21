package br.com.celke.cursos.shared.web;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Id de correlação da requisição ({@code X-Request-Id}). Aceita o id recebido (do Caddy ou do
 * cliente) só se for curto e seguro para o log; senão gera um novo.
 */
public final class IdRequisicao {

    public static final String CABECALHO = "X-Request-Id";
    public static final String CHAVE_MDC = "requestId";

    private static final Pattern FORMATO_VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private IdRequisicao() {}

    public static String normalizar(String recebido) {
        if (recebido != null && FORMATO_VALIDO.matcher(recebido).matches()) {
            return recebido;
        }
        return UUID.randomUUID().toString();
    }
}
