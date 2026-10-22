package br.com.celke.cursos.forum.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.armazenamento.StorageService.InfoArquivo;
import br.com.celke.cursos.shared.imagem.ImagemInvalidaException;
import br.com.celke.cursos.shared.imagem.ProcessadorImagem;
import java.net.URI;
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

@ExtendWith(MockitoExtension.class)
class AnexoForumServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final String U1 = "uploads/forum/7/0b6b6b6e-1c1d-4e4f-8a8b-9c9d0e0f1a1b";
    private static final String U2 = "uploads/forum/7/1c7c7c7f-2d2e-4f5a-9b9c-0d0e1f2a3b4c";

    @Mock
    private StorageService storage;

    @Mock
    private ProcessadorImagem processador;

    private AnexoForumService service;

    @BeforeEach
    void criar() {
        service = new AnexoForumService(
                storage,
                processador,
                new ForumProperties(5, Duration.ofHours(1), 3),
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("envio: URL em uploads/forum/<usuario>/<uuid> para JPEG ou PNG até 5 MB")
    void iniciarEnvio() throws Exception {
        when(storage.urlUpload(startsWith("uploads/forum/7/"), eq("image/png"), eq(Duration.ofMinutes(10))))
                .thenReturn(URI.create("http://media.localhost/put").toURL());

        var envio = service.iniciarEnvio(7L, "image/png", AnexoForumService.TAMANHO_MAXIMO);

        assertThat(envio.chave()).matches("uploads/forum/7/[0-9a-f-]{36}");
        assertThat(envio.expiraEm()).isEqualTo(AGORA.plus(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("envio recusado: tipo que não é imagem aceita e tamanho acima de 5 MB")
    void envioRecusado() {
        assertThatThrownBy(() -> service.iniciarEnvio(7L, "image/svg+xml", 10))
                .isInstanceOf(ImagemInvalidaException.class)
                .hasMessage("Anexe uma imagem JPEG ou PNG.");
        assertThatThrownBy(() -> service.iniciarEnvio(7L, "image/png", AnexoForumService.TAMANHO_MAXIMO + 1))
                .isInstanceOf(ImagemInvalidaException.class)
                .hasMessage("A imagem pode ter no máximo 5 MB.");
        verify(storage, never()).urlUpload(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("processa: regrava em JPEG em forum/anexos/ e apaga o envio")
    void processa() {
        when(storage.info(U1)).thenReturn(Optional.of(new InfoArquivo(100, "image/png")));
        when(storage.baixar(U1)).thenReturn(new byte[] {1});
        when(processador.normalizar(new byte[] {1}, 1600, 1600)).thenReturn(new byte[] {9, 9});

        var gravados = service.processar(7L, List.of(U1));

        assertThat(gravados).singleElement().satisfies(g -> {
            assertThat(g.chave()).matches("forum/anexos/[0-9a-f-]{36}\\.jpg");
            assertThat(g.tamanho()).isEqualTo(2);
        });
        verify(storage).gravar(startsWith("forum/anexos/"), eq(new byte[] {9, 9}), eq("image/jpeg"));
        verify(storage).excluir(U1);
    }

    @Test
    @DisplayName("IDOR: chave enviada por outro usuário (ou fora do formato) é recusada sem tocar no bucket")
    void chaveDeOutroUsuario() {
        for (String chave : List.of(
                "uploads/forum/8/0b6b6b6e-1c1d-4e4f-8a8b-9c9d0e0f1a1b",
                "forum/anexos/x.jpg",
                "uploads/forum/7/../8/x")) {
            assertThatThrownBy(() -> service.processar(7L, List.of(chave)))
                    .isInstanceOf(ImagemInvalidaException.class)
                    .hasMessage("Anexo inválido. Envie a imagem de novo.");
        }
        verify(storage, never()).info(anyString());
    }

    @Test
    @DisplayName("mais anexos que o permitido ou a mesma imagem duas vezes são recusados")
    void quantidade() {
        assertThatThrownBy(() -> service.processar(7L, List.of(U1, U2, U1 + "a", U2 + "b")))
                .isInstanceOf(ImagemInvalidaException.class)
                .hasMessage("Anexe no máximo 3 imagens.");
        assertThatThrownBy(() -> service.processar(7L, List.of(U1, U1)))
                .isInstanceOf(ImagemInvalidaException.class)
                .hasMessage("A mesma imagem foi anexada duas vezes.");
        assertThat(service.processar(7L, null)).isEmpty();
        assertThat(service.processar(7L, List.of())).isEmpty();
    }

    @Test
    @DisplayName("falha na segunda imagem desfaz a primeira já gravada")
    void falhaDesfaz() {
        when(storage.info(U1)).thenReturn(Optional.of(new InfoArquivo(100, "image/png")));
        when(storage.baixar(U1)).thenReturn(new byte[] {1});
        when(processador.normalizar(new byte[] {1}, 1600, 1600)).thenReturn(new byte[] {9});
        when(storage.info(U2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.processar(7L, List.of(U1, U2)))
                .isInstanceOf(ImagemInvalidaException.class)
                .hasMessage("A imagem não foi enviada. Envie a imagem de novo.");
        verify(storage).excluir(startsWith("forum/anexos/"));
    }

    @Test
    @DisplayName("imagem acima de 5 MB enviada mesmo assim é recusada e o envio apagado")
    void grandeDemais() {
        when(storage.info(U1))
                .thenReturn(Optional.of(new InfoArquivo(AnexoForumService.TAMANHO_MAXIMO + 1, "image/png")));

        assertThatThrownBy(() -> service.processar(7L, List.of(U1))).hasMessage("A imagem pode ter no máximo 5 MB.");
        verify(storage).excluir(U1);
        verify(storage, never()).gravar(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("descartar apaga as imagens gravadas")
    void descartar() {
        service.descartar(List.of(new AnexoForumService.AnexoGravado("forum/anexos/a.jpg", 1)));

        verify(storage).excluir("forum/anexos/a.jpg");
    }
}
