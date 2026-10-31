package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.auth.application.AuthService.Sessao;
import br.com.celke.cursos.auth.application.AuthService.SessaoSegundoFator;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Resposta do login e do refresh. O access token vai no corpo e o front o guarda só em memória;
 * o refresh token vai apenas no cookie {@code HttpOnly}. Os códigos de recuperação do segundo fator
 * só aparecem uma vez, quando ele acaba de ser ativado.
 */
public record SessaoResponse(
        String accessToken,
        String tipo,
        Instant expiraEm,
        UsuarioResponse usuario,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> codigosRecuperacao) {

    public static SessaoResponse de(Sessao sessao) {
        return new SessaoResponse(
                sessao.acesso().valor(),
                "Bearer",
                sessao.acesso().expiraEm(),
                UsuarioResponse.de(sessao.usuario()),
                List.of());
    }

    public static SessaoResponse de(SessaoSegundoFator sessao) {
        SessaoResponse base = de(sessao.sessao());
        return new SessaoResponse(
                base.accessToken(), base.tipo(), base.expiraEm(), base.usuario(), sessao.codigosRecuperacao());
    }
}
