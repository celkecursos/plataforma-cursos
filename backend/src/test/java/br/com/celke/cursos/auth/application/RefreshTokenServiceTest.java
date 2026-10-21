package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.auth.domain.RefreshToken;
import br.com.celke.cursos.auth.infra.RefreshTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant AGORA = Instant.parse("2026-01-10T12:00:00Z");
    private static final UUID FAMILIA = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String TOKEN = "token-do-cookie";

    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenService service;

    @BeforeEach
    void criar() {
        var props = new AuthProperties(
                "segredo", "plataforma-cursos", Duration.ofMinutes(10), Duration.ofDays(14), true, List.of());
        service = new RefreshTokenService(repository, props, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static RefreshToken tokenGravado(Instant expiraEm) {
        return new RefreshToken(7L, FAMILIA, RefreshTokenService.hash(TOKEN), expiraEm, AGORA.minusSeconds(60));
    }

    @Test
    @DisplayName("hash é o SHA-256 em hexadecimal")
    void hashSha256() {
        assertThat(RefreshTokenService.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("emitir grava só o hash, numa família nova, com validade de 14 dias")
    void emitir() {
        var emitido = service.emitir(7L);

        ArgumentCaptor<RefreshToken> salvo = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(salvo.capture());
        assertThat(salvo.getValue().getHash())
                .isEqualTo(RefreshTokenService.hash(emitido.token()))
                .isNotEqualTo(emitido.token());
        assertThat(salvo.getValue().getUsuarioId()).isEqualTo(7L);
        assertThat(emitido.expiraEm()).isEqualTo(AGORA.plus(Duration.ofDays(14)));
        assertThat(emitido.token()).hasSize(43); // 32 bytes em Base64 URL sem padding
    }

    @Test
    @DisplayName("cada emissão gera um token diferente")
    void tokensDiferentes() {
        assertThat(service.emitir(7L).token()).isNotEqualTo(service.emitir(7L).token());
    }

    @Test
    @DisplayName("rotacionar marca o token como usado e emite outro na mesma família")
    void rotacionar() {
        RefreshToken atual = tokenGravado(AGORA.plusSeconds(1));
        when(repository.findByHash(RefreshTokenService.hash(TOKEN))).thenReturn(Optional.of(atual));

        var novo = service.rotacionar(TOKEN);

        assertThat(novo).isPresent();
        assertThat(atual.foiUsado()).isTrue();
        ArgumentCaptor<RefreshToken> salvo = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(salvo.capture());
        assertThat(salvo.getValue().getFamilia()).isEqualTo(FAMILIA);
        assertThat(salvo.getValue().getUsuarioId()).isEqualTo(7L);
    }

    @ParameterizedTest(name = "[{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("token ausente não consulta o banco")
    void tokenAusente(String token) {
        assertThat(service.rotacionar(token)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("token desconhecido é recusado")
    void tokenDesconhecido() {
        when(repository.findByHash(anyString())).thenReturn(Optional.empty());

        assertThat(service.rotacionar(TOKEN)).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("token expira no instante exato da validade")
    void expiradoNoInstanteExato() {
        when(repository.findByHash(anyString())).thenReturn(Optional.of(tokenGravado(AGORA)));

        assertThat(service.rotacionar(TOKEN)).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("token revogado é recusado sem revogar de novo")
    void revogado() {
        RefreshToken revogado = tokenGravado(AGORA.plusSeconds(60));
        ReflectionTestUtils.setField(revogado, "revogadoEm", AGORA.minusSeconds(1));
        when(repository.findByHash(anyString())).thenReturn(Optional.of(revogado));

        assertThat(service.rotacionar(TOKEN)).isEmpty();
        verify(repository, never()).save(any());
        verify(repository, never()).revogarFamilia(any(), any());
    }

    @Test
    @DisplayName("token já usado revoga a família inteira e não emite outro")
    void reutilizado() {
        RefreshToken usado = tokenGravado(AGORA.plusSeconds(60));
        usado.marcarUsado(AGORA.minusSeconds(5));
        when(repository.findByHash(anyString())).thenReturn(Optional.of(usado));

        assertThat(service.rotacionar(TOKEN)).isEmpty();
        verify(repository).revogarFamilia(FAMILIA, AGORA);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("logout revoga a família do token")
    void logout() {
        when(repository.findByHash(RefreshTokenService.hash(TOKEN)))
                .thenReturn(Optional.of(tokenGravado(AGORA.plusSeconds(60))));

        service.revogarFamiliaDoToken(TOKEN);

        verify(repository).revogarFamilia(FAMILIA, AGORA);
    }

    @Test
    @DisplayName("logout sem token não consulta o banco")
    void logoutSemToken() {
        service.revogarFamiliaDoToken(null);

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("sair de todos revoga todas as famílias do usuário")
    void sairDeTodos() {
        service.revogarTodas(7L);

        verify(repository).revogarTodasDoUsuario(7L, AGORA);
    }
}
