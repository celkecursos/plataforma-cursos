package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.auth.domain.TipoToken;
import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.support.fixtures.UsuarioFixture;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
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
class CadastroServiceTest {

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private TokenUsoUnicoService tokens;

    @Mock
    private EmailService emailService;

    private CadastroService service;

    @BeforeEach
    void criar() {
        service = new CadastroService(
                usuarioService,
                tokens,
                emailService,
                new ContaProperties("http://localhost:5173/", Duration.ofHours(48), Duration.ofHours(1), 3));
    }

    private static Usuario ana() {
        return UsuarioFixture.usuario(7, "ana@celke.uno", "hash", Papel.ALUNO);
    }

    private void esperarToken(String token) {
        when(tokens.emitir(7L, TipoToken.CONFIRMACAO_EMAIL)).thenReturn(Optional.of(token));
        when(tokens.validade(TipoToken.CONFIRMACAO_EMAIL)).thenReturn(Duration.ofHours(48));
    }

    @Test
    @DisplayName("e-mail novo: cria a conta pendente e envia o link de confirmação")
    void emailNovo() {
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.empty());
        when(usuarioService.cadastrarAluno("Ana", "ana@celke.uno", "senha-da-ana"))
                .thenReturn(UsuarioFixture.naoConfirmado(ana()));
        esperarToken("abc");

        service.cadastrar("Ana", "ana@celke.uno", "senha-da-ana");

        verify(emailService)
                .enviar(
                        "ana@celke.uno",
                        "Confirme o seu e-mail",
                        "confirmacao-email",
                        Map.of(
                                "nome", "Usuário 7",
                                "link", "http://localhost:5173/confirmar-email?token=abc",
                                "validade", "48 horas"));
    }

    @Test
    @DisplayName("e-mail de conta ativa: não cria nada e avisa o dono só uma vez por hora")
    void emailExistente() {
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(ana()));

        service.cadastrar("Outra Pessoa", "ana@celke.uno", "girassol-azul-77");
        service.cadastrar("Outra Pessoa", "ana@celke.uno", "girassol-azul-77");

        verify(usuarioService, never()).cadastrarAluno(any(), any(), any());
        verify(emailService, times(1))
                .enviar(
                        "ana@celke.uno",
                        "Tentativa de cadastro com o seu e-mail",
                        "cadastro-existente",
                        Map.of(
                                "nome", "Usuário 7",
                                "linkEntrar", "http://localhost:5173/entrar",
                                "linkRecuperar", "http://localhost:5173/recuperar-senha"));
    }

    @Test
    @DisplayName("e-mail com cadastro pendente: reenvia o link, sem criar outra conta")
    void emailPendente() {
        when(usuarioService.buscarPorEmail("ana@celke.uno"))
                .thenReturn(Optional.of(UsuarioFixture.naoConfirmado(ana())));
        esperarToken("novo");

        service.cadastrar("Ana", "ana@celke.uno", "senha-da-ana");

        verify(usuarioService, never()).cadastrarAluno(any(), any(), any());
        verify(emailService).enviar(eq("ana@celke.uno"), eq("Confirme o seu e-mail"), eq("confirmacao-email"), any());
    }

    @Test
    @DisplayName("e-mail de conta inativa: nada acontece")
    void contaInativa() {
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(UsuarioFixture.inativo(ana())));

        service.cadastrar("Ana", "ana@celke.uno", "senha-da-ana");

        verify(emailService, never()).enviar(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("limite de e-mails atingido: a conta é criada, mas o link não é enviado")
    void limiteDeEmails() {
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.empty());
        when(usuarioService.cadastrarAluno(any(), any(), any())).thenReturn(UsuarioFixture.naoConfirmado(ana()));
        when(tokens.emitir(7L, TipoToken.CONFIRMACAO_EMAIL)).thenReturn(Optional.empty());

        service.cadastrar("Ana", "ana@celke.uno", "senha-da-ana");

        verify(emailService, never()).enviar(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("cadastro simultâneo com o mesmo e-mail não vira erro para o cliente")
    void cadastroSimultaneo() {
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.empty());
        when(usuarioService.cadastrarAluno(any(), any(), any()))
                .thenThrow(new ConflitoException("Já existe um usuário com este e-mail."));

        assertThatCode(() -> service.cadastrar("Ana", "ana@celke.uno", "senha-da-ana"))
                .doesNotThrowAnyException();
        verify(emailService, never()).enviar(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("reenviar confirmação só envia para conta ativa com e-mail pendente")
    void reenviar() {
        when(usuarioService.buscarPorEmail("pendente@celke.uno"))
                .thenReturn(Optional.of(UsuarioFixture.naoConfirmado(ana())));
        when(usuarioService.buscarPorEmail("confirmada@celke.uno")).thenReturn(Optional.of(ana()));
        when(usuarioService.buscarPorEmail("ninguem@celke.uno")).thenReturn(Optional.empty());
        esperarToken("abc");

        service.reenviarConfirmacao("pendente@celke.uno");
        service.reenviarConfirmacao("confirmada@celke.uno");
        service.reenviarConfirmacao("ninguem@celke.uno");

        verify(emailService, times(1)).enviar(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("confirmar com token válido confirma o e-mail do dono")
    void confirmar() {
        Usuario pendente = UsuarioFixture.naoConfirmado(ana());
        when(tokens.consumir("abc", TipoToken.CONFIRMACAO_EMAIL)).thenReturn(Optional.of(7L));
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(pendente));

        service.confirmarEmail("abc");

        verify(usuarioService).confirmarEmail(pendente);
    }

    @Test
    @DisplayName("confirmar com token inválido é recusado sem confirmar nada")
    void confirmarTokenInvalido() {
        when(tokens.consumir("abc", TipoToken.CONFIRMACAO_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmarEmail("abc"))
                .isInstanceOf(LinkInvalidoException.class)
                .hasMessage("Link inválido ou expirado. Peça um novo.");
        verify(usuarioService, never()).confirmarEmail(any());
    }
}
