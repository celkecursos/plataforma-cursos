package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;

/**
 * Cobre: o que a API aceita como access token — assinatura, algoritmo, emissor, validade e papéis
 * (401 para token inválido, 403 para papel insuficiente).
 * Não cobre: emissão no login (AuthLoginIT).
 * Dado criado: nenhum.
 */
class AuthTokenIT extends IntegrationTest {

    private static final String ROTA_ADMIN = "/teste/erros/admin";

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    @DisplayName("token válido de admin acessa rota de admin")
    void tokenValidoAdmin() {
        assertThat(get(ROTA_ADMIN, login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA)))
                .hasStatusOk();
    }

    @Test
    @DisplayName("token válido de aluno em rota de admin recebe 403")
    void tokenAlunoEmRotaAdmin() {
        assertThat(get(ROTA_ADMIN, login(UsuariosSeed.ALUNO_EMAIL, UsuariosSeed.SENHA)))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Acesso negado.");
    }

    @Test
    @DisplayName("token adulterado (papel trocado no payload) é recusado")
    void tokenAdulterado() {
        String[] partes = login(UsuariosSeed.ALUNO_EMAIL, UsuariosSeed.SENHA).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8)
                .replace("\"ALUNO\"", "\"ADMIN\"");
        String adulterado = partes[0] + "." + base64Url(payload) + "." + partes[2];

        assertNaoAutenticado(get(ROTA_ADMIN, adulterado));
    }

    @Test
    @DisplayName("token expirado é recusado")
    void tokenExpirado() {
        Instant agora = Instant.now();
        String token = assinar(
                jwtEncoder,
                MacAlgorithm.HS256,
                claims(agora.minus(Duration.ofMinutes(20)), agora.minusSeconds(1), "plataforma-cursos"));

        assertNaoAutenticado(get("/api/auth/me", token));
    }

    @Test
    @DisplayName("token assinado com outra chave é recusado")
    void outraChave() {
        var outraChave = new SecretKeySpec(
                "outra-chave-com-mais-de-trinta-e-dois-bytes!!".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        String token =
                assinar(new NimbusJwtEncoder(new ImmutableSecret<>(outraChave)), MacAlgorithm.HS256, claimsValidos());

        assertNaoAutenticado(get("/api/auth/me", token));
    }

    @Test
    @DisplayName("token com algoritmo diferente de HS256 é recusado, mesmo assinado com a chave certa")
    void outroAlgoritmo() {
        String token = assinar(jwtEncoder, MacAlgorithm.HS384, claimsValidos());

        assertNaoAutenticado(get("/api/auth/me", token));
    }

    @Test
    @DisplayName("token sem assinatura (alg none) é recusado")
    void algNone() {
        String payload = "{\"iss\":\"plataforma-cursos\",\"sub\":\"1\",\"papeis\":[\"ADMIN\"],\"exp\":%d}"
                .formatted(Instant.now().plusSeconds(600).getEpochSecond());
        String token = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "." + base64Url(payload) + ".";

        assertNaoAutenticado(get(ROTA_ADMIN, token));
    }

    @Test
    @DisplayName("token de outro emissor é recusado")
    void outroEmissor() {
        Instant agora = Instant.now();
        String token = assinar(jwtEncoder, MacAlgorithm.HS256, claims(agora, agora.plusSeconds(600), "outro-sistema"));

        assertNaoAutenticado(get("/api/auth/me", token));
    }

    @Test
    @DisplayName("cabeçalho Authorization malformado é recusado")
    void cabecalhoMalformado() {
        assertNaoAutenticado(mvc.get().uri("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer nao-e-um-jwt"));
    }

    private MockMvcRequestBuilder get(String rota, String token) {
        return mvc.get().uri(rota).header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    private void assertNaoAutenticado(MockMvcRequestBuilder requisicao) {
        assertThat(requisicao)
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Autenticação necessária.");
    }

    private static JwtClaimsSet claimsValidos() {
        Instant agora = Instant.now();
        return claims(agora, agora.plusSeconds(600), "plataforma-cursos");
    }

    private static JwtClaimsSet claims(Instant emitidoEm, Instant expiraEm, String emissor) {
        return JwtClaimsSet.builder()
                .issuer(emissor)
                .subject("1")
                .issuedAt(emitidoEm)
                .expiresAt(expiraEm)
                .claim("papeis", List.of("ADMIN"))
                .build();
    }

    private static String assinar(JwtEncoder encoder, MacAlgorithm algoritmo, JwtClaimsSet claims) {
        return encoder.encode(
                        JwtEncoderParameters.from(JwsHeader.with(algoritmo).build(), claims))
                .getTokenValue();
    }

    private static String base64Url(String texto) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }
}
