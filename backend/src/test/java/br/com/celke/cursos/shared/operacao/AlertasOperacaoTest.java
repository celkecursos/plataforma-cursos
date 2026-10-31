package br.com.celke.cursos.shared.operacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.shared.fila.FilaService;
import br.com.celke.cursos.shared.fila.FilaService.FalhaJob;
import br.com.celke.cursos.shared.operacao.MedidorDisco.Espaco;
import br.com.celke.cursos.support.fixtures.RelogioAjustavel;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
class AlertasOperacaoTest {

    private static final Instant T0 = Instant.parse("2026-03-10T12:00:00Z");
    private static final long GB = 1024L * 1024 * 1024;

    @Mock
    private FilaService fila;

    @Mock
    private EmailService emailService;

    @Mock
    private MedidorDisco medidorDisco;

    private RelogioAjustavel relogio;

    @BeforeEach
    void criarRelogio() {
        relogio = new RelogioAjustavel(T0);
    }

    private AlertasOperacao alertas(String email) {
        return new AlertasOperacao(
                fila,
                emailService,
                medidorDisco,
                new AlertasProperties(email, 15, Path.of("/"), Duration.ofHours(6)),
                relogio);
    }

    private void disco(long livreGb) {
        when(medidorDisco.medir(Path.of("/"))).thenReturn(new Espaco(livreGb * GB, 100 * GB));
    }

    @Test
    @DisplayName("sem e-mail configurado, nada é verificado")
    void desligado() {
        alertas(" ").verificar();

        verify(fila, never()).falhasDesde(any(), anyString());
        verify(medidorDisco, never()).medir(any());
    }

    @Test
    @DisplayName("jobs com erro desde a última verificação geram um e-mail com o resumo; o de e-mail fica de fora")
    @SuppressWarnings("unchecked")
    void falhas() {
        disco(50);
        when(fila.falhasDesde(T0.minus(Duration.ofMinutes(15)), EmailService.TIPO_JOB))
                .thenReturn(List.of(new FalhaJob(9, "video.transcodificar", "ffmpeg\n  falhou", T0)));

        alertas("ops@celke.uno").verificar();

        ArgumentCaptor<Map<String, String>> variaveis = ArgumentCaptor.forClass(Map.class);
        verify(emailService)
                .enviar(
                        eq("ops@celke.uno"),
                        eq("[Plataforma de Cursos] 1 job(s) com erro no worker"),
                        eq("alerta-operacao"),
                        variaveis.capture());
        assertThat(variaveis.getValue().get("detalhes")).isEqualTo("Job 9 (video.transcodificar): ffmpeg falhou");
    }

    @Test
    @DisplayName("a verificação seguinte procura falhas só depois da anterior")
    void janela() {
        disco(50);
        when(fila.falhasDesde(any(), anyString())).thenReturn(List.of());
        AlertasOperacao alertas = alertas("ops@celke.uno");

        alertas.verificar();
        relogio.avancar(Duration.ofMinutes(15));
        alertas.verificar();

        verify(fila).falhasDesde(T0, EmailService.TIPO_JOB);
        verify(emailService, never()).enviar(anyString(), anyString(), anyString(), anyMap());
    }

    @ParameterizedTest(name = "{0} GB livres de 100 → alerta: {1}")
    @CsvSource({"14, true", "15, false", "50, false", "0, true"})
    @DisplayName("disco abaixo de 15% livre gera alerta; no limite, não")
    void disco(long livre, boolean alerta) {
        disco(livre);
        when(fila.falhasDesde(any(), anyString())).thenReturn(List.of());

        alertas("ops@celke.uno").verificar();

        verify(emailService, times(alerta ? 1 : 0))
                .enviar(
                        eq("ops@celke.uno"),
                        eq("[Plataforma de Cursos] Disco com %d%% livre".formatted(livre)),
                        eq("alerta-operacao"),
                        anyMap());
    }

    @Test
    @DisplayName("disco baixo repete o alerta só depois de 6 horas; recuperado, o próximo alerta sai na hora")
    void repeticaoDoDisco() {
        when(fila.falhasDesde(any(), anyString())).thenReturn(List.of());
        when(medidorDisco.medir(Path.of("/")))
                .thenReturn(new Espaco(10 * GB, 100 * GB))
                .thenReturn(new Espaco(10 * GB, 100 * GB))
                .thenReturn(new Espaco(10 * GB, 100 * GB))
                .thenReturn(new Espaco(50 * GB, 100 * GB))
                .thenReturn(new Espaco(10 * GB, 100 * GB));
        AlertasOperacao alertas = alertas("ops@celke.uno");

        alertas.verificar(); // alerta
        relogio.avancar(Duration.ofHours(1));
        alertas.verificar(); // silêncio
        relogio.avancar(Duration.ofHours(5));
        alertas.verificar(); // 6 h depois: alerta
        relogio.avancar(Duration.ofMinutes(15));
        alertas.verificar(); // recuperou
        relogio.avancar(Duration.ofMinutes(15));
        alertas.verificar(); // caiu de novo: alerta

        verify(emailService, times(3)).enviar(anyString(), anyString(), eq("alerta-operacao"), anyMap());
    }

    @Test
    @DisplayName("percentual livre arredondado para baixo; disco de tamanho zero conta como cheio")
    void percentual() {
        assertThat(new Espaco(149, 1000).percentualLivre()).isEqualTo(14);
        assertThat(new Espaco(0, 0).percentualLivre()).isZero();
        assertThat(new MedidorDisco().medir(Path.of(".")).total()).isPositive();
    }
}
