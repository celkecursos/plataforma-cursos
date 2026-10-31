package br.com.celke.cursos.shared.fila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessadorFilaTest {

    private static final Instant AGORA = Instant.parse("2026-01-10T12:00:00Z");

    @Mock
    private FilaService fila;

    @Mock
    private ManipuladorJob manipulador;

    private ProcessadorFila processador;

    @BeforeEach
    void criar() {
        when(manipulador.tipo()).thenReturn("EMAIL");
        processador = new ProcessadorFila(fila, List.of(manipulador), Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static Job job(int tentativas) {
        return new Job(7, "EMAIL", "{}", tentativas, 5);
    }

    @Test
    @DisplayName("sem job disponível devolve false e não chama o manipulador")
    void filaVazia() throws Exception {
        when(fila.reservar(Set.of("EMAIL"), AGORA)).thenReturn(Optional.empty());

        assertThat(processador.processarProximo()).isFalse();
        verify(manipulador, never()).processar(any());
    }

    @Test
    @DisplayName("job processado com sucesso é concluído")
    void sucesso() throws Exception {
        Job job = job(1);
        when(fila.reservar(Set.of("EMAIL"), AGORA)).thenReturn(Optional.of(job));

        assertThat(processador.processarProximo()).isTrue();

        verify(manipulador).processar(job);
        verify(fila).concluir(job, AGORA);
        verify(fila, never()).falhar(any(), anyString(), any());
    }

    @Test
    @DisplayName("falha comum agenda nova tentativa com o erro")
    void falhaComum() throws Exception {
        Job job = job(1);
        when(fila.reservar(Set.of("EMAIL"), AGORA)).thenReturn(Optional.of(job));
        doThrow(new IllegalStateException("SMTP fora do ar")).when(manipulador).processar(job);

        assertThat(processador.processarProximo()).isTrue();

        verify(fila).falhar(job, "java.lang.IllegalStateException: SMTP fora do ar", AGORA);
        verify(fila, never()).concluir(any(), any());
    }

    @Test
    @DisplayName("erro permanente vai direto para ERRO, sem nova tentativa")
    void erroPermanente() throws Exception {
        Job job = job(1);
        when(fila.reservar(Set.of("EMAIL"), AGORA)).thenReturn(Optional.of(job));
        doThrow(new ErroPermanenteException("Nome de template inválido."))
                .when(manipulador)
                .processar(job);

        processador.processarProximo();

        verify(fila).falharDefinitivo(job, "Nome de template inválido.", AGORA);
        verify(fila, never()).falhar(any(), anyString(), any());
    }

    @Test
    @DisplayName("job que passou do limite de tentativas vai para ERRO sem ser processado")
    void tentativasExcedidas() throws Exception {
        Job job = job(6);
        when(fila.reservar(Set.of("EMAIL"), AGORA)).thenReturn(Optional.of(job));

        assertThat(processador.processarProximo()).isTrue();

        verify(manipulador, never()).processar(any());
        verify(fila)
                .falharDefinitivo(eq(job), eq("Tentativas esgotadas (o processamento foi interrompido)."), eq(AGORA));
    }

    @Test
    @DisplayName("falha comum antes da última tentativa não avisa o manipulador")
    void falhaComumNaoDesiste() throws Exception {
        Job job = job(4);
        when(fila.reservar(Set.of("EMAIL"), AGORA)).thenReturn(Optional.of(job));
        doThrow(new IllegalStateException("fora do ar")).when(manipulador).processar(job);

        processador.processarProximo();

        verify(manipulador, never()).desistir(any(), anyString());
    }

    @Test
    @DisplayName("falha comum na última tentativa avisa o manipulador da desistência")
    void falhaNaUltimaTentativaDesiste() throws Exception {
        Job job = job(5);
        when(fila.reservar(Set.of("EMAIL"), AGORA)).thenReturn(Optional.of(job));
        doThrow(new IllegalStateException("fora do ar")).when(manipulador).processar(job);

        processador.processarProximo();

        verify(fila).falhar(job, "java.lang.IllegalStateException: fora do ar", AGORA);
        verify(manipulador).desistir(job, "java.lang.IllegalStateException: fora do ar");
    }

    @Test
    @DisplayName("erro permanente e tentativas excedidas avisam o manipulador")
    void desistencias() throws Exception {
        Job permanente = job(1);
        Job excedido = job(6);
        when(fila.reservar(Set.of("EMAIL"), AGORA))
                .thenReturn(Optional.of(permanente))
                .thenReturn(Optional.of(excedido));
        doThrow(new ErroPermanenteException("Arquivo inválido."))
                .when(manipulador)
                .processar(permanente);

        processador.processarProximo();
        processador.processarProximo();

        verify(manipulador).desistir(permanente, "Arquivo inválido.");
        verify(manipulador).desistir(excedido, "Tentativas esgotadas (o processamento foi interrompido).");
    }

    @Test
    @DisplayName("falha do manipulador ao registrar a desistência não desfaz o estado da fila")
    void falhaAoDesistir() throws Exception {
        Job job = job(6);
        when(fila.reservar(Set.of("EMAIL"), AGORA)).thenReturn(Optional.of(job));
        doThrow(new IllegalStateException("banco fora do ar")).when(manipulador).desistir(eq(job), anyString());

        assertThat(processador.processarProximo()).isTrue();

        verify(fila).falharDefinitivo(eq(job), anyString(), eq(AGORA));
    }

    @Test
    @DisplayName("job na última tentativa permitida ainda é processado")
    void ultimaTentativaPermitida() throws Exception {
        Job job = job(5);
        when(fila.reservar(Set.of("EMAIL"), AGORA)).thenReturn(Optional.of(job));

        processador.processarProximo();

        verify(manipulador).processar(job);
        verify(fila).concluir(job, AGORA);
    }
}
