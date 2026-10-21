package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.auth.application.AuthService.Sessao;
import java.time.Instant;

/**
 * Resposta do login e do refresh. O access token vai no corpo e o front o guarda só em memória;
 * o refresh token vai apenas no cookie {@code HttpOnly}.
 */
public record SessaoResponse(String accessToken, String tipo, Instant expiraEm, UsuarioResponse usuario) {

    public static SessaoResponse de(Sessao sessao) {
        return new SessaoResponse(
                sessao.acesso().valor(), "Bearer", sessao.acesso().expiraEm(), UsuarioResponse.de(sessao.usuario()));
    }
}
