package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.auth.domain.TipoToken;
import br.com.celke.cursos.auth.domain.TokenUsoUnico;
import br.com.celke.cursos.auth.infra.TokenUsoUnicoRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenUsoUnicoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-01-10T12:00:00Z");
    private static final String TOKEN = "token-do-link";

    @Mock
    private TokenUsoUnicoRepository repository;

    private TokenUsoUnicoService service;

    @BeforeEach
    void criar() {
        var props = new ContaProperties("http://localhost:5173", Duration.ofHours(48), Duration.ofHours(1), 3);
        service = new TokenUsoUnicoService(repository, props, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static TokenUsoUnico gravado(TipoToken tipo, Instant expiraEm) {
        return new TokenUsoUnico(7L, tipo, TokenSeguro.hash(TOKEN), expiraEm, AGORA.minusSeconds(60));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"CONFIRMACAO_EMAIL, 48", "RECUPERACAO_SENHA, 1"})
    @DisplayName("emitir invalida os pendentes, grava só o hash e usa a validade do tipo")
    void emitir(TipoToken tipo, long horas) {
        when(repository.countByUsuarioIdAndTipoAndCriadoEmAfter(7L, tipo, AGORA.minus(Duration.ofHours(1))))
                .thenReturn(2L);

        String token = service.emitir(7L, tipo).orElseThrow();

        verify(repository).invalidarPendentes(7L, tipo, AGORA);
        ArgumentCaptor<TokenUsoUnico> salvo = ArgumentCaptor.forClass(TokenUsoUnico.class);
        verify(repository).save(salvo.capture());
        assertThat(salvo.getValue().getHash())
                .isEqualTo(TokenSeguro.hash(token))
                .isNotEqualTo(token);
        assertThat(salvo.getValue().getExpiraEm()).isEqualTo(AGORA.plus(Duration.ofHours(horas)));
        assertThat(salvo.getValue().getTipo()).isEqualTo(tipo);
    }

    @Test
    @DisplayName("com 3 e-mails na última hora não emite nem invalida nada")
    void limitePorHora() {
        when(repository.countByUsuarioIdAndTipoAndCriadoEmAfter(any(), any(), any()))
                .thenReturn(3L);

        assertThat(service.emitir(7L, TipoToken.RECUPERACAO_SENHA)).isEmpty();
        verify(repository, never()).invalidarPendentes(any(), any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("consumir marca o token como usado e devolve o dono")
    void consumir() {
        TokenUsoUnico token = gravado(TipoToken.CONFIRMACAO_EMAIL, AGORA.plusSeconds(1));
        when(repository.findByHash(TokenSeguro.hash(TOKEN))).thenReturn(Optional.of(token));

        assertThat(service.consumir(TOKEN, TipoToken.CONFIRMACAO_EMAIL)).contains(7L);
        assertThat(token.getUsadoEm()).isEqualTo(AGORA);
    }

    @Test
    @DisplayName("token expira no instante exato da validade")
    void expirado() {
        when(repository.findByHash(anyString())).thenReturn(Optional.of(gravado(TipoToken.CONFIRMACAO_EMAIL, AGORA)));

        assertThat(service.consumir(TOKEN, TipoToken.CONFIRMACAO_EMAIL)).isEmpty();
    }

    @Test
    @DisplayName("token já usado (ou invalidado por um mais novo) é recusado")
    void jaUsado() {
        TokenUsoUnico token = gravado(TipoToken.CONFIRMACAO_EMAIL, AGORA.plusSeconds(60));
        token.marcarUsado(AGORA.minusSeconds(1));
        when(repository.findByHash(anyString())).thenReturn(Optional.of(token));

        assertThat(service.consumir(TOKEN, TipoToken.CONFIRMACAO_EMAIL)).isEmpty();
    }

    @Test
    @DisplayName("token de confirmação não serve para redefinir senha")
    void outroTipo() {
        TokenUsoUnico token = gravado(TipoToken.CONFIRMACAO_EMAIL, AGORA.plusSeconds(60));
        when(repository.findByHash(anyString())).thenReturn(Optional.of(token));

        assertThat(service.consumir(TOKEN, TipoToken.RECUPERACAO_SENHA)).isEmpty();
        assertThat(token.getUsadoEm()).isNull();
    }

    @Test
    @DisplayName("token desconhecido é recusado")
    void desconhecido() {
        when(repository.findByHash(anyString())).thenReturn(Optional.empty());

        assertThat(service.consumir(TOKEN, TipoToken.CONFIRMACAO_EMAIL)).isEmpty();
    }

    @Test
    @DisplayName("token em branco não consulta o banco")
    void emBranco() {
        assertThat(service.consumir(" ", TipoToken.CONFIRMACAO_EMAIL)).isEmpty();
        assertThat(service.consumir(null, TipoToken.CONFIRMACAO_EMAIL)).isEmpty();
        verifyNoInteractions(repository);
    }
}
