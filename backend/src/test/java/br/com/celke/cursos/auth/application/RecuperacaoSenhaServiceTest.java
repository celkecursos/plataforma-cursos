package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.auth.domain.TipoToken;
import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.shared.erro.CampoInvalidoException;
import br.com.celke.cursos.support.fixtures.UsuarioFixture;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.PoliticaSenha;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecuperacaoSenhaServiceTest {

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private TokenUsoUnicoService tokens;

    @Mock
    private EmailService emailService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private RecuperacaoSenhaService service;

    @BeforeEach
    void criar() {
        service = new RecuperacaoSenhaService(
                usuarioService,
                tokens,
                emailService,
                refreshTokenService,
                new ContaProperties("http://localhost:5173", Duration.ofHours(48), Duration.ofHours(1), 3));
    }

    private static Usuario ana() {
        return UsuarioFixture.usuario(7, "ana@celke.uno", "hash", Papel.ALUNO);
    }

    @Test
    @DisplayName("conta ativa recebe o link de 1 hora para criar nova senha")
    void solicitar() {
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(ana()));
        when(tokens.emitir(7L, TipoToken.RECUPERACAO_SENHA)).thenReturn(Optional.of("abc"));
        when(tokens.validade(TipoToken.RECUPERACAO_SENHA)).thenReturn(Duration.ofHours(1));

        service.solicitar("ana@celke.uno");

        verify(emailService)
                .enviar(
                        "ana@celke.uno",
                        "Crie uma nova senha",
                        "recuperacao-senha",
                        Map.of(
                                "nome", "Usuário 7",
                                "link", "http://localhost:5173/redefinir-senha?token=abc",
                                "validade", "1 hora"));
    }

    @Test
    @DisplayName("e-mail sem conta ou de conta inativa não recebe nada e não gera token")
    void semConta() {
        when(usuarioService.buscarPorEmail("ninguem@celke.uno")).thenReturn(Optional.empty());
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(UsuarioFixture.inativo(ana())));

        service.solicitar("ninguem@celke.uno");
        service.solicitar("ana@celke.uno");

        verify(tokens, never()).emitir(anyLong(), any());
        verify(emailService, never()).enviar(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("limite de e-mails atingido: nada é enviado")
    void limiteDeEmails() {
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(ana()));
        when(tokens.emitir(7L, TipoToken.RECUPERACAO_SENHA)).thenReturn(Optional.empty());

        service.solicitar("ana@celke.uno");

        verify(emailService, never()).enviar(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("redefinir troca a senha, confirma o e-mail e encerra todas as sessões")
    void redefinir() {
        Usuario ana = ana();
        when(tokens.dono("abc", TipoToken.RECUPERACAO_SENHA)).thenReturn(Optional.of(7L));
        when(tokens.consumir("abc", TipoToken.RECUPERACAO_SENHA)).thenReturn(Optional.of(7L));
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(ana));

        service.redefinir("abc", "nova-senha-da-ana");

        verify(usuarioService).trocarSenha(ana, "nova-senha-da-ana");
        verify(usuarioService).confirmarEmail(ana);
        verify(refreshTokenService).revogarTodas(7L);
    }

    @Test
    @DisplayName("token inválido é recusado sem trocar a senha nem encerrar sessões")
    void tokenInvalido() {
        when(tokens.dono("abc", TipoToken.RECUPERACAO_SENHA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redefinir("abc", "nova-senha-da-ana"))
                .isInstanceOf(LinkInvalidoException.class);
        verify(usuarioService, never()).trocarSenha(any(), any());
        verify(refreshTokenService, never()).revogarTodas(anyLong());
    }

    @Test
    @DisplayName("senha fora da política é recusada no campo sem gastar o link")
    void senhaFraca() {
        when(tokens.dono("abc", TipoToken.RECUPERACAO_SENHA)).thenReturn(Optional.of(7L));
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(ana()));

        assertThatThrownBy(() -> service.redefinir("abc", "1234567890"))
                .isInstanceOf(CampoInvalidoException.class)
                .hasMessage(PoliticaSenha.COMUM);
        verify(tokens, never()).consumir(anyString(), any());
        verify(usuarioService, never()).trocarSenha(any(), any());
    }

    @Test
    @DisplayName("conta desativada depois do pedido não troca a senha")
    void contaInativa() {
        when(tokens.dono("abc", TipoToken.RECUPERACAO_SENHA)).thenReturn(Optional.of(7L));
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(UsuarioFixture.inativo(ana())));

        assertThatThrownBy(() -> service.redefinir("abc", "nova-senha-da-ana"))
                .isInstanceOf(LinkInvalidoException.class);
        verify(usuarioService, never()).trocarSenha(any(), any());
    }
}
