package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.support.fixtures.UsuarioFixture;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class SessaoValidaValidatorTest {

    private static final Instant EMITIDO = Instant.parse("2026-01-10T12:00:00Z");

    @Mock
    private UsuarioService usuarioService;

    private SessaoValidaValidator validador;

    @BeforeEach
    void criar() {
        validador = new SessaoValidaValidator(usuarioService);
    }

    private static Jwt jwt(String sub) {
        return Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .subject(sub)
                .issuedAt(EMITIDO)
                .build();
    }

    private static Usuario ana() {
        return UsuarioFixture.usuario(7, "ana@celke.uno", "hash", Papel.ALUNO);
    }

    @Test
    @DisplayName("conta ativa e sem encerramento: válido")
    void ativa() {
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(ana()));
        assertThat(validador.validate(jwt("7")).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("conta bloqueada, inexistente ou subject inválido: recusado")
    void recusado() {
        Usuario bloqueada = ana();
        bloqueada.bloquear(EMITIDO.minusSeconds(60));
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(bloqueada));
        when(usuarioService.buscarPorId(8L)).thenReturn(Optional.empty());

        assertThat(validador.validate(jwt("7")).hasErrors()).isTrue();
        assertThat(validador.validate(jwt("8")).hasErrors()).isTrue();
        assertThat(validador.validate(jwt("x")).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("token emitido antes do encerramento das sessões é recusado; depois, vale")
    void encerramento() {
        Usuario ana = ana();
        ana.encerrarSessoes(EMITIDO.plusSeconds(1));
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(ana));
        assertThat(validador.validate(jwt("7")).hasErrors()).isTrue();

        ana.encerrarSessoes(EMITIDO.minusSeconds(1));
        validador.esquecer(7L);
        assertThat(validador.validate(jwt("7")).hasErrors()).isFalse();
    }
}
