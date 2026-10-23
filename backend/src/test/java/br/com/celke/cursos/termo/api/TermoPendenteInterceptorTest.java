package br.com.celke.cursos.termo.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.termo.application.TermoPendenteException;
import br.com.celke.cursos.termo.application.TermoService;
import br.com.celke.cursos.termo.domain.Termo;
import br.com.celke.cursos.termo.domain.TipoTermo;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TermoPendenteInterceptorTest {

    @Mock
    private TermoService termoService;

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticar(long usuarioId) {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .subject(String.valueOf(usuarioId))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private boolean chamar() throws Exception {
        return new TermoPendenteInterceptor(termoService)
                .preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());
    }

    @Test
    @DisplayName("sem termo pendente, a requisição segue")
    void semPendencia() throws Exception {
        autenticar(7);
        when(termoService.pendentes(7L)).thenReturn(List.of());

        assertThat(chamar()).isTrue();
    }

    @Test
    @DisplayName("com termo pendente, 403 com a lista")
    void comPendencia() {
        autenticar(7);
        Termo termo = new Termo(TipoTermo.TERMOS_USO, 1, "T", 1L, Instant.now());
        ReflectionTestUtils.setField(termo, "id", 5L);
        when(termoService.pendentes(7L)).thenReturn(List.of(termo));

        assertThatThrownBy(this::chamar).isInstanceOf(TermoPendenteException.class);
    }

    @Test
    @DisplayName("sem autenticação por token (a segurança já respondeu 401), não consulta termos")
    void semToken() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "k", "anonimo", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(chamar()).isTrue();
        verify(termoService, never()).pendentes(any());
    }
}
