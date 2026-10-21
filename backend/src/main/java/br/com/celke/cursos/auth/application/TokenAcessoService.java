package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.shared.security.SecurityConfig;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Emite o access token (JWT): {@code sub} = id do usuário e {@code papeis} = papéis dele. */
@Service
public class TokenAcessoService {

    private final JwtEncoder encoder;
    private final AuthProperties props;
    private final Clock clock;

    public TokenAcessoService(JwtEncoder encoder, AuthProperties props, Clock clock) {
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    public TokenAcesso emitir(Usuario usuario) {
        Instant agora = clock.instant();
        Instant expiraEm = agora.plus(props.validadeAcesso());
        List<String> papeis =
                usuario.getPapeis().stream().map(Enum::name).sorted().toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.emissor())
                .subject(usuario.getId().toString())
                .issuedAt(agora)
                .expiresAt(expiraEm)
                .claim(SecurityConfig.CLAIM_PAPEIS, papeis)
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new TokenAcesso(token, expiraEm);
    }

    public record TokenAcesso(String valor, Instant expiraEm) {}
}
