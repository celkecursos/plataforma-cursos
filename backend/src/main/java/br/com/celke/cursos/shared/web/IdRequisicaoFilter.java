package br.com.celke.cursos.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Primeiro filtro da requisição (antes da segurança): coloca o id de correlação no MDC, para todo
 * log da requisição carregar o mesmo id, e devolve o id no cabeçalho da resposta.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class IdRequisicaoFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = IdRequisicao.normalizar(request.getHeader(IdRequisicao.CABECALHO));
        MDC.put(IdRequisicao.CHAVE_MDC, id);
        response.setHeader(IdRequisicao.CABECALHO, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(IdRequisicao.CHAVE_MDC);
        }
    }
}
