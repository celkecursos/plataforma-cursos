package br.com.celke.cursos.shared.email;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.celke.cursos.shared.fila.FilaService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private static final Instant AGORA = Instant.parse("2026-01-10T12:00:00Z");

    @Mock
    private FilaService fila;

    private EmailService emailService;

    @BeforeEach
    void criar() {
        emailService = new EmailService(fila, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("enfileira o e-mail com o destinatário sem espaços nas pontas")
    void enfileira() {
        emailService.enviar(" ana@teste.celke.uno ", "Assunto", "confirmacao-email", Map.of("nome", "Ana"));

        verify(fila)
                .enfileirar(
                        "EMAIL",
                        new MensagemEmail("ana@teste.celke.uno", "Assunto", "confirmacao-email", Map.of("nome", "Ana")),
                        AGORA);
    }

    @ParameterizedTest(name = "[{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "sem-arroba"})
    @DisplayName("rejeita destinatário inválido sem enfileirar")
    void destinatarioInvalido(String para) {
        assertThatThrownBy(() -> emailService.enviar(para, "Assunto", "teste", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Destinatário inválido.");
        verify(fila, never()).enfileirar(anyString(), any(), any());
    }

    @ParameterizedTest(name = "[{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"../segredo", "email/teste", "Teste", "teste.html", "teste com espaço"})
    @DisplayName("rejeita nome de template fora do padrão sem enfileirar")
    void templateInvalido(String template) {
        assertThatThrownBy(() -> emailService.enviar("ana@teste.celke.uno", "Assunto", template, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Nome de template inválido.");
        verify(fila, never()).enfileirar(anyString(), any(), any());
    }
}
