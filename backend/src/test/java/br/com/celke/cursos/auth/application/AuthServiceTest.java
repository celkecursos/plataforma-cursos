package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.auth.application.RefreshTokenService.RefreshEmitido;
import br.com.celke.cursos.auth.application.TokenAcessoService.TokenAcesso;
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
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant EXPIRA = Instant.parse("2026-01-10T12:10:00Z");
    private static final String HASH_FICTICIO = "{argon2}ficticio";

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenAcessoService tokenAcessoService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthService service;

    @BeforeEach
    void criar() {
        // O construtor calcula o hash fictício usado quando o e-mail não existe
        when(passwordEncoder.encode(anyString())).thenReturn(HASH_FICTICIO);
        service = new AuthService(usuarioService, passwordEncoder, tokenAcessoService, refreshTokenService);
    }

    private static Usuario ana() {
        return UsuarioFixture.usuario(7, "ana@celke.uno", "{argon2}hash-da-ana", Papel.ALUNO);
    }

    @Test
    @DisplayName("login correto emite access token e refresh token, e regrava hash antigo se preciso")
    void loginCorreto() {
        Usuario ana = ana();
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(ana));
        when(passwordEncoder.matches("senha-certa", "{argon2}hash-da-ana")).thenReturn(true);
        when(refreshTokenService.emitir(7L)).thenReturn(new RefreshEmitido("refresh", 7L, EXPIRA));
        when(tokenAcessoService.emitir(ana)).thenReturn(new TokenAcesso("jwt", EXPIRA));

        var sessao = service.entrar("ana@celke.uno", "senha-certa");

        assertThat(sessao.acesso().valor()).isEqualTo("jwt");
        assertThat(sessao.refresh().token()).isEqualTo("refresh");
        verify(usuarioService).atualizarHashSeNecessario(ana, "senha-certa");
    }

    @Test
    @DisplayName("senha errada é recusada sem emitir tokens")
    void senhaErrada() {
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(ana()));
        when(passwordEncoder.matches("senha-errada", "{argon2}hash-da-ana")).thenReturn(false);

        assertThatThrownBy(() -> service.entrar("ana@celke.uno", "senha-errada"))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage("E-mail ou senha inválidos.");
        verify(refreshTokenService, never()).emitir(anyLong());
        verify(usuarioService, never()).atualizarHashSeNecessario(any(), any());
    }

    @Test
    @DisplayName("e-mail inexistente confere a senha contra o hash fictício e recebe a mesma mensagem")
    void emailInexistente() {
        when(usuarioService.buscarPorEmail("ninguem@celke.uno")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.entrar("ninguem@celke.uno", "qualquer"))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage("E-mail ou senha inválidos.");
        verify(passwordEncoder).matches("qualquer", HASH_FICTICIO);
        verify(refreshTokenService, never()).emitir(anyLong());
    }

    @Test
    @DisplayName("usuário inativo é recusado mesmo com a senha certa")
    void usuarioInativo() {
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(UsuarioFixture.inativo(ana())));
        when(passwordEncoder.matches("senha-certa", "{argon2}hash-da-ana")).thenReturn(true);

        assertThatThrownBy(() -> service.entrar("ana@celke.uno", "senha-certa"))
                .isInstanceOf(CredenciaisInvalidasException.class);
        verify(refreshTokenService, never()).emitir(anyLong());
    }

    @Test
    @DisplayName("renovar emite novo access token para o dono do refresh")
    void renovar() {
        Usuario ana = ana();
        when(refreshTokenService.rotacionar("refresh")).thenReturn(Optional.of(new RefreshEmitido("novo", 7L, EXPIRA)));
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(ana));
        when(tokenAcessoService.emitir(ana)).thenReturn(new TokenAcesso("jwt", EXPIRA));

        var sessao = service.renovar("refresh");

        assertThat(sessao.refresh().token()).isEqualTo("novo");
        assertThat(sessao.acesso().valor()).isEqualTo("jwt");
    }

    @Test
    @DisplayName("refresh recusado vira sessão inválida")
    void refreshRecusado() {
        when(refreshTokenService.rotacionar("refresh")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renovar("refresh"))
                .isInstanceOf(SessaoInvalidaException.class)
                .hasMessage("Sessão expirada. Entre novamente.");
        verify(tokenAcessoService, never()).emitir(any());
    }

    @Test
    @DisplayName("refresh de usuário desativado vira sessão inválida")
    void renovarUsuarioInativo() {
        when(refreshTokenService.rotacionar("refresh")).thenReturn(Optional.of(new RefreshEmitido("novo", 7L, EXPIRA)));
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(UsuarioFixture.inativo(ana())));

        assertThatThrownBy(() -> service.renovar("refresh")).isInstanceOf(SessaoInvalidaException.class);
        verify(tokenAcessoService, never()).emitir(any());
    }

    @Test
    @DisplayName("sair e sair de todos delegam a revogação")
    void sair() {
        service.sair("refresh");
        service.sairDeTodos(7L);

        verify(refreshTokenService).revogarFamiliaDoToken("refresh");
        verify(refreshTokenService).revogarTodas(7L);
    }
}
