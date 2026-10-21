package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.auth.application.AuthProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie do refresh token: {@code HttpOnly} (JavaScript não lê), {@code SameSite=Strict}, enviado
 * só para {@code /api/auth} e <strong>sem</strong> {@code Domain}, para ficar preso ao host da API e
 * não ir para as outras aplicações do mesmo domínio.
 */
@Component
public class CookieRefresh {

    public static final String NOME = "refresh_token";
    static final String CAMINHO = "/api/auth";

    private final AuthProperties props;

    public CookieRefresh(AuthProperties props) {
        this.props = props;
    }

    /** Cookie de um refresh token recém-emitido: vale o mesmo que o token. */
    public ResponseCookie criar(String token) {
        return base(token, props.validadeRefresh());
    }

    public ResponseCookie limpar() {
        return base("", Duration.ZERO);
    }

    private ResponseCookie base(String valor, Duration maxAge) {
        return ResponseCookie.from(NOME, valor)
                .httpOnly(true)
                .secure(props.cookieSeguro())
                .sameSite("Strict")
                .path(CAMINHO)
                .maxAge(maxAge)
                .build();
    }
}
