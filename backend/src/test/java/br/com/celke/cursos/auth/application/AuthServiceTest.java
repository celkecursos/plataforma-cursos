package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.auth.application.AuthService.Desafio;
import br.com.celke.cursos.auth.application.AuthService.Sessao;
import br.com.celke.cursos.auth.application.RefreshTokenService.RefreshEmitido;
import br.com.celke.cursos.auth.application.SegundoFatorService.Verificacao;
import br.com.celke.cursos.auth.application.TokenAcessoService.TokenAcesso;
import br.com.celke.cursos.auth.domain.ResultadoLogin;
import br.com.celke.cursos.auth.domain.TipoToken;
import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.support.fixtures.UsuarioFixture;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
    private static final OrigemRequisicao ORIGEM = new OrigemRequisicao("203.0.113.7", "Navegador/1.0");

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenAcessoService tokenAcessoService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private BloqueioLoginService bloqueioLoginService;

    @Mock
    private AuditoriaLoginService auditoriaLoginService;

    @Mock
    private SegundoFatorService segundoFatorService;

    @Mock
    private TokenUsoUnicoService tokens;

    private AuthService service;

    @BeforeEach
    void criar() {
        // O construtor calcula o hash fictício usado quando o e-mail não existe
        when(passwordEncoder.encode(anyString())).thenReturn(HASH_FICTICIO);
        service = new AuthService(
                usuarioService,
                passwordEncoder,
                tokenAcessoService,
                refreshTokenService,
                bloqueioLoginService,
                auditoriaLoginService,
                segundoFatorService,
                tokens,
                Clock.fixed(Instant.parse("2026-01-10T12:00:00Z"), ZoneOffset.UTC));
    }

    private static Usuario ana() {
        return UsuarioFixture.usuario(7, "ana@celke.uno", "{argon2}hash-da-ana", Papel.ALUNO);
    }

    @Test
    @DisplayName("login correto emite os tokens, zera o bloqueio, audita SUCESSO e regrava hash antigo se preciso")
    void loginCorreto() {
        Usuario ana = ana();
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(ana));
        when(passwordEncoder.matches("senha-certa", "{argon2}hash-da-ana")).thenReturn(true);
        when(refreshTokenService.emitir(7L)).thenReturn(new RefreshEmitido("refresh", 7L, EXPIRA));
        when(tokenAcessoService.emitir(ana)).thenReturn(new TokenAcesso("jwt", EXPIRA));

        var sessao = (Sessao) service.entrar("ana@celke.uno", "senha-certa", ORIGEM);

        assertThat(sessao.acesso().valor()).isEqualTo("jwt");
        assertThat(sessao.refresh().token()).isEqualTo("refresh");
        verify(auditoriaLoginService).registrar("ana@celke.uno", ana, ResultadoLogin.SUCESSO, ORIGEM);
        verify(bloqueioLoginService).registrarSucesso("ana@celke.uno", "203.0.113.7");
        verify(bloqueioLoginService, never()).registrarFalha(anyString(), anyString());
        verify(usuarioService).atualizarHashSeNecessario(ana, "senha-certa");
    }

    @Test
    @DisplayName("senha errada conta falha, audita SENHA_INVALIDA e não emite tokens")
    void senhaErrada() {
        Usuario ana = ana();
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(ana));
        when(passwordEncoder.matches("senha-errada", "{argon2}hash-da-ana")).thenReturn(false);

        assertThatThrownBy(() -> service.entrar("ana@celke.uno", "senha-errada", ORIGEM))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage("E-mail ou senha inválidos.");
        verify(auditoriaLoginService).registrar("ana@celke.uno", ana, ResultadoLogin.SENHA_INVALIDA, ORIGEM);
        verify(bloqueioLoginService).registrarFalha("ana@celke.uno", "203.0.113.7");
        verify(refreshTokenService, never()).emitir(anyLong());
        verify(usuarioService, never()).atualizarHashSeNecessario(any(), any());
    }

    @Test
    @DisplayName("e-mail inexistente confere a senha contra o hash fictício, conta falha e audita EMAIL_DESCONHECIDO")
    void emailInexistente() {
        when(usuarioService.buscarPorEmail("ninguem@celke.uno")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.entrar("ninguem@celke.uno", "qualquer", ORIGEM))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage("E-mail ou senha inválidos.");
        verify(passwordEncoder).matches("qualquer", HASH_FICTICIO);
        verify(auditoriaLoginService).registrar("ninguem@celke.uno", null, ResultadoLogin.EMAIL_DESCONHECIDO, ORIGEM);
        verify(bloqueioLoginService).registrarFalha("ninguem@celke.uno", "203.0.113.7");
        verify(refreshTokenService, never()).emitir(anyLong());
    }

    @Test
    @DisplayName("usuário inativo com a senha certa é recusado e auditado como USUARIO_INATIVO")
    void usuarioInativo() {
        Usuario inativa = UsuarioFixture.inativo(ana());
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(inativa));
        when(passwordEncoder.matches("senha-certa", "{argon2}hash-da-ana")).thenReturn(true);

        assertThatThrownBy(() -> service.entrar("ana@celke.uno", "senha-certa", ORIGEM))
                .isInstanceOf(CredenciaisInvalidasException.class);
        verify(auditoriaLoginService).registrar("ana@celke.uno", inativa, ResultadoLogin.USUARIO_INATIVO, ORIGEM);
        verify(bloqueioLoginService).registrarFalha("ana@celke.uno", "203.0.113.7");
        verify(refreshTokenService, never()).emitir(anyLong());
    }

    @Test
    @DisplayName("senha certa com e-mail não confirmado recebe 403 próprio, sem tokens, e zera o bloqueio")
    void emailNaoConfirmado() {
        Usuario pendente = UsuarioFixture.naoConfirmado(ana());
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(pendente));
        when(passwordEncoder.matches("senha-certa", "{argon2}hash-da-ana")).thenReturn(true);

        assertThatThrownBy(() -> service.entrar("ana@celke.uno", "senha-certa", ORIGEM))
                .isInstanceOf(EmailNaoConfirmadoException.class)
                .hasMessage("Confirme o seu e-mail antes de entrar. Se não recebeu o link, peça um novo.");
        verify(auditoriaLoginService).registrar("ana@celke.uno", pendente, ResultadoLogin.EMAIL_NAO_CONFIRMADO, ORIGEM);
        verify(bloqueioLoginService).registrarSucesso("ana@celke.uno", "203.0.113.7");
        verify(bloqueioLoginService, never()).registrarFalha(anyString(), anyString());
        verify(refreshTokenService, never()).emitir(anyLong());
    }

    @Test
    @DisplayName("e-mail não confirmado com a senha errada recebe a mensagem genérica")
    void emailNaoConfirmadoSenhaErrada() {
        when(usuarioService.buscarPorEmail("ana@celke.uno"))
                .thenReturn(Optional.of(UsuarioFixture.naoConfirmado(ana())));
        when(passwordEncoder.matches("senha-errada", "{argon2}hash-da-ana")).thenReturn(false);

        assertThatThrownBy(() -> service.entrar("ana@celke.uno", "senha-errada", ORIGEM))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    @DisplayName("login bloqueado é recusado antes de buscar o usuário ou calcular hash, e auditado como BLOQUEADO")
    void bloqueado() {
        doThrow(new MuitasTentativasException(Duration.ofMinutes(15)))
                .when(bloqueioLoginService)
                .verificar("ana@celke.uno", "203.0.113.7");

        assertThatThrownBy(() -> service.entrar("ana@celke.uno", "senha-certa", ORIGEM))
                .isInstanceOf(MuitasTentativasException.class)
                .hasMessage("Muitas tentativas. Tente novamente em 15 minutos.");
        verify(auditoriaLoginService).registrar("ana@celke.uno", null, ResultadoLogin.BLOQUEADO, ORIGEM);
        verify(usuarioService, never()).buscarPorEmail(any());
        verify(passwordEncoder, never()).matches(any(), any());
        verify(bloqueioLoginService, never()).registrarFalha(anyString(), anyString());
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

    private static Usuario admin() {
        return UsuarioFixture.usuario(9, "admin@celke.uno", "{argon2}hash-do-admin", Papel.ADMIN);
    }

    @Test
    @DisplayName("admin com a senha certa recebe o desafio, sem sessão, e o bloqueio não zera")
    void adminRecebeDesafio() {
        Usuario admin = admin();
        when(usuarioService.buscarPorEmail("admin@celke.uno")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("senha-certa", "{argon2}hash-do-admin")).thenReturn(true);
        when(tokens.emitir(9L, TipoToken.DESAFIO_SEGUNDO_FATOR)).thenReturn(Optional.of("desafio"));
        when(tokens.validade(TipoToken.DESAFIO_SEGUNDO_FATOR)).thenReturn(Duration.ofMinutes(5));
        when(segundoFatorService.ativo(9L)).thenReturn(false);

        var entrada = service.entrar("admin@celke.uno", "senha-certa", ORIGEM);

        assertThat(entrada).isEqualTo(new Desafio("desafio", true, Instant.parse("2026-01-10T12:05:00Z")));
        verify(auditoriaLoginService)
                .registrar("admin@celke.uno", admin, ResultadoLogin.SEGUNDO_FATOR_PENDENTE, ORIGEM);
        verify(refreshTokenService, never()).emitir(anyLong());
        verify(bloqueioLoginService, never()).registrarSucesso(anyString(), anyString());
    }

    @Test
    @DisplayName("código errado conta falha, audita e não gasta o desafio")
    void codigoErrado() {
        Usuario admin = admin();
        when(tokens.dono("desafio", TipoToken.DESAFIO_SEGUNDO_FATOR)).thenReturn(Optional.of(9L));
        when(usuarioService.buscarPorId(9L)).thenReturn(Optional.of(admin));
        when(segundoFatorService.verificar(admin, "000000", null)).thenReturn(new Verificacao(null, List.of()));

        assertThatThrownBy(() -> service.concluirSegundoFator("desafio", "000000", null, ORIGEM))
                .isInstanceOf(CodigoInvalidoException.class);
        verify(auditoriaLoginService)
                .registrar("admin@celke.uno", admin, ResultadoLogin.SEGUNDO_FATOR_INVALIDO, ORIGEM);
        verify(bloqueioLoginService).registrarFalha("admin@celke.uno", "203.0.113.7");
        verify(tokens, never()).consumir(anyString(), any());
        verify(refreshTokenService, never()).emitir(anyLong());
    }

    @Test
    @DisplayName("código certo gasta o desafio, zera o bloqueio, audita e abre a sessão")
    void codigoCerto() {
        Usuario admin = admin();
        when(tokens.dono("desafio", TipoToken.DESAFIO_SEGUNDO_FATOR)).thenReturn(Optional.of(9L));
        when(tokens.consumir("desafio", TipoToken.DESAFIO_SEGUNDO_FATOR)).thenReturn(Optional.of(9L));
        when(usuarioService.buscarPorId(9L)).thenReturn(Optional.of(admin));
        when(segundoFatorService.verificar(admin, "123456", null))
                .thenReturn(new Verificacao(ResultadoLogin.SEGUNDO_FATOR_CADASTRADO, List.of("abcde-fghjk")));
        when(refreshTokenService.emitir(9L)).thenReturn(new RefreshEmitido("refresh", 9L, EXPIRA));
        when(tokenAcessoService.emitir(admin)).thenReturn(new TokenAcesso("jwt", EXPIRA));

        var sessao = service.concluirSegundoFator("desafio", "123456", null, ORIGEM);

        assertThat(sessao.sessao().acesso().valor()).isEqualTo("jwt");
        assertThat(sessao.codigosRecuperacao()).containsExactly("abcde-fghjk");
        verify(auditoriaLoginService)
                .registrar("admin@celke.uno", admin, ResultadoLogin.SEGUNDO_FATOR_CADASTRADO, ORIGEM);
        verify(bloqueioLoginService).registrarSucesso("admin@celke.uno", "203.0.113.7");
    }

    @Test
    @DisplayName("desafio vencido, usado ou de conta inativa é recusado")
    void desafioInvalido() {
        when(tokens.dono("velho", TipoToken.DESAFIO_SEGUNDO_FATOR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.concluirSegundoFator("velho", "123456", null, ORIGEM))
                .isInstanceOf(DesafioInvalidoException.class);
        assertThatThrownBy(() -> service.cadastrarSegundoFator("velho")).isInstanceOf(DesafioInvalidoException.class);
    }
}
