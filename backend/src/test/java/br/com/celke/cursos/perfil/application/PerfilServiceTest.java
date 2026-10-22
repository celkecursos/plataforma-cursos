package br.com.celke.cursos.perfil.application;

import static br.com.celke.cursos.support.fixtures.UsuarioFixture.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.auth.application.RefreshTokenService;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.armazenamento.StorageService.InfoArquivo;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import br.com.celke.cursos.shared.imagem.ImagemInvalidaException;
import br.com.celke.cursos.shared.imagem.ProcessadorImagem;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PerfilServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final String UPLOAD = "uploads/fotos/7/0b6b6b6e-1c1d-4e4f-8a8b-9c9d0e0f1a1b";

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private StorageService storage;

    @Mock
    private ProcessadorImagem processador;

    private PerfilService service;
    private final Usuario aluno = usuario(7, "ana@celke.uno", "hash", Papel.ALUNO);
    private final Usuario admin = usuario(1, "adm@celke.uno", "hash", Papel.ADMIN);

    @BeforeEach
    void criar() {
        service = new PerfilService(
                usuarioService, refreshTokenService, storage, processador, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Nested
    class Senha {

        @Test
        @DisplayName("troca conferindo a atual e encerra todas as sessões")
        void troca() {
            when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(aluno));
            when(usuarioService.senhaConfere(aluno, "senha-atual-123")).thenReturn(true);
            when(usuarioService.senhaConfere(aluno, "senha-nova-456")).thenReturn(false);

            service.trocarSenha(7L, "senha-atual-123", "senha-nova-456");

            verify(usuarioService).trocarSenha(aluno, "senha-nova-456");
            verify(refreshTokenService).revogarTodas(7L);
        }

        @Test
        @DisplayName("senha atual errada: 400 e nada muda")
        void atualErrada() {
            when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(aluno));
            when(usuarioService.senhaConfere(aluno, "errada")).thenReturn(false);

            assertThatThrownBy(() -> service.trocarSenha(7L, "errada", "senha-nova-456"))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("A senha atual não confere.");
            verify(usuarioService, never()).trocarSenha(any(), anyString());
            verify(refreshTokenService, never()).revogarTodas(any());
        }

        @Test
        @DisplayName("nova senha curta para o papel: aluno 10, admin 12")
        void politicaPorPapel() {
            when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(aluno));
            when(usuarioService.buscarPorId(1L)).thenReturn(Optional.of(admin));
            when(usuarioService.senhaConfere(any(), eq("atual"))).thenReturn(true);

            assertThatThrownBy(() -> service.trocarSenha(7L, "atual", "123456789"))
                    .hasMessage("A senha deve ter pelo menos 10 caracteres.");
            assertThatThrownBy(() -> service.trocarSenha(1L, "atual", "12345678901"))
                    .hasMessage("A senha deve ter pelo menos 12 caracteres.");
            verify(usuarioService, never()).trocarSenha(any(), anyString());
        }

        @Test
        @DisplayName("nova senha igual à atual é recusada")
        void igual() {
            when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(aluno));
            when(usuarioService.senhaConfere(aluno, "mesma-senha-123")).thenReturn(true);

            assertThatThrownBy(() -> service.trocarSenha(7L, "mesma-senha-123", "mesma-senha-123"))
                    .hasMessage("A nova senha precisa ser diferente da atual.");
        }

        @Test
        @DisplayName("usuário do token que não existe mais: sessão inválida")
        void semUsuario() {
            when(usuarioService.buscarPorId(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.buscar(9L))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("Sessão inválida. Entre novamente.");
        }
    }

    @Nested
    class Foto {

        @Test
        @DisplayName("envio em uploads/fotos/<usuario>/<uuid> para JPEG ou PNG até 5 MB")
        void iniciar() throws Exception {
            when(storage.urlUpload(startsWith("uploads/fotos/7/"), eq("image/jpeg"), eq(Duration.ofMinutes(10))))
                    .thenReturn(URI.create("http://media.localhost/put").toURL());

            assertThat(service.iniciarEnvioFoto(7L, "image/jpeg", PerfilService.TAMANHO_MAXIMO_FOTO)
                            .chave())
                    .matches("uploads/fotos/7/[0-9a-f-]{36}");
            assertThatThrownBy(() -> service.iniciarEnvioFoto(7L, "image/gif", 1))
                    .isInstanceOf(ImagemInvalidaException.class)
                    .hasMessage("Envie uma imagem JPEG ou PNG.");
            assertThatThrownBy(() -> service.iniciarEnvioFoto(7L, "image/png", PerfilService.TAMANHO_MAXIMO_FOTO + 1))
                    .hasMessage("A foto pode ter no máximo 5 MB.");
        }

        @Test
        @DisplayName("confirma: regrava 400 × 400 em JPEG, apaga o envio e a foto anterior")
        void confirma() {
            when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(aluno));
            when(storage.info(UPLOAD)).thenReturn(Optional.of(new InfoArquivo(100, "image/png")));
            when(storage.baixar(UPLOAD)).thenReturn(new byte[] {1});
            when(processador.normalizar(new byte[] {1}, 400, 400)).thenReturn(new byte[] {9});
            when(usuarioService.trocarFoto(eq(7L), startsWith("fotos/7/")))
                    .thenReturn(Optional.of("fotos/7/antiga.jpg"));

            service.confirmarFoto(7L, UPLOAD);

            verify(storage).gravar(startsWith("fotos/7/"), eq(new byte[] {9}), eq("image/jpeg"));
            verify(storage).excluir(UPLOAD);
            verify(storage).excluir("fotos/7/antiga.jpg");
        }

        @Test
        @DisplayName("IDOR: chave enviada por outro usuário é recusada sem tocar no bucket")
        void chaveDeOutro() {
            when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(aluno));

            assertThatThrownBy(() -> service.confirmarFoto(7L, "uploads/fotos/8/0b6b6b6e-1c1d-4e4f-8a8b-9c9d0e0f1a1b"))
                    .isInstanceOf(ImagemInvalidaException.class)
                    .hasMessage("Envio de foto inválido. Comece um novo envio.");
            verify(storage, never()).info(anyString());
        }

        @Test
        @DisplayName("remover apaga a foto atual")
        void remover() {
            when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(aluno));
            when(usuarioService.trocarFoto(7L, null)).thenReturn(Optional.of("fotos/7/x.jpg"));

            service.removerFoto(7L);

            verify(storage).excluir("fotos/7/x.jpg");
        }

        @Test
        @DisplayName("URL da foto só quando há foto")
        void url() throws Exception {
            assertThat(service.urlFoto(aluno)).isEmpty();
            aluno.trocarFoto("fotos/7/x.jpg", AGORA);
            when(storage.urlDownload("fotos/7/x.jpg", Duration.ofHours(1)))
                    .thenReturn(URI.create("http://media.localhost/x").toURL());

            assertThat(service.urlFoto(aluno)).isPresent();
        }
    }
}
