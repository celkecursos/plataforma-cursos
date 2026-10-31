package br.com.celke.cursos.shared.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP e user-agent de quem fez a requisição, para auditoria e limites. O IP já vem corrigido pelo
 * {@code server.forward-headers-strategy}: atrás do Caddy é o IP do cliente, não o do proxy.
 */
public record OrigemRequisicao(String ip, String userAgent) {

    public static final int TAMANHO_MAXIMO_USER_AGENT = 255;

    public OrigemRequisicao {
        if (userAgent != null && userAgent.length() > TAMANHO_MAXIMO_USER_AGENT) {
            userAgent = userAgent.substring(0, TAMANHO_MAXIMO_USER_AGENT);
        }
    }

    public static OrigemRequisicao de(HttpServletRequest request) {
        return new OrigemRequisicao(request.getRemoteAddr(), request.getHeader("User-Agent"));
    }
}
