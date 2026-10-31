package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.support.fixtures.UsuarioFixture;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;

/** Emissão com o encoder real e conferência com o decoder real da {@link JwtConfig}. */
class TokenAcessoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-01-10T12:00:00Z");
    private static final AuthProperties PROPS = new AuthProperties(
            "dGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUt",
            "plataforma-cursos",
            Duration.ofMinutes(10),
            Duration.ofDays(14),
            true,
            List.of());

    private final JwtConfig config = new JwtConfig();
    private final TokenAcessoService service =
            new TokenAcessoService(config.jwtEncoder(PROPS), PROPS, Clock.fixed(AGORA, ZoneOffset.UTC));

    @Test
    @DisplayName("token leva emissor, id do usuário, papéis ordenados e validade de 10 minutos")
    void claims() {
        var usuario = UsuarioFixture.usuario(42, "ana@celke.uno", "hash", Papel.ADMIN, Papel.ALUNO);

        var token = service.emitir(usuario);
        Jwt jwt = decoderEm(AGORA.plusSeconds(1)).decode(token.valor());

        assertThat(jwt.getClaimAsString("iss")).isEqualTo("plataforma-cursos");
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsStringList("papeis")).containsExactly("ADMIN", "ALUNO");
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(jwt.getIssuedAt()).isEqualTo(AGORA);
        assertThat(jwt.getExpiresAt()).isEqualTo(AGORA.plus(Duration.ofMinutes(10)));
        assertThat(token.expiraEm()).isEqualTo(jwt.getExpiresAt());
    }

    @Test
    @DisplayName("token vale até um segundo antes do fim e é recusado no instante em que expira")
    void validade() {
        var token = service.emitir(UsuarioFixture.usuario(42, "ana@celke.uno", "hash", Papel.ALUNO));
        Instant expira = AGORA.plus(Duration.ofMinutes(10));

        assertThat(decoderEm(expira.minusSeconds(1)).decode(token.valor())).isNotNull();
        assertThatThrownBy(() -> decoderEm(expira.plusSeconds(1)).decode(token.valor()))
                .isInstanceOf(JwtValidationException.class);
    }

    private org.springframework.security.oauth2.jwt.JwtDecoder decoderEm(Instant instante) {
        // Conta sempre ativa: a validação da situação da conta tem teste próprio
        UsuarioService usuarios = Mockito.mock(UsuarioService.class);
        Mockito.when(usuarios.buscarPorId(Mockito.anyLong()))
                .thenReturn(Optional.of(UsuarioFixture.usuario(42, "ana@celke.uno", "hash", Papel.ALUNO)));
        return config.jwtDecoder(PROPS, Clock.fixed(instante, ZoneOffset.UTC), new SessaoValidaValidator(usuarios));
    }
}
