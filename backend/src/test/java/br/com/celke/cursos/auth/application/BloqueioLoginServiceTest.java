package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import br.com.celke.cursos.support.fixtures.RelogioAjustavel;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BloqueioLoginServiceTest {

    private static final String EMAIL = "ana@celke.uno";
    private static final String IP = "203.0.113.7";

    // 5 falhas por e-mail + IP, 20 por e-mail, janela de 1 h, bloqueio de 15 min
    private final LimitesProperties props =
            new LimitesProperties(30, 5, 20, Duration.ofHours(1), Duration.ofMinutes(15));

    private RelogioAjustavel relogio;
    private BloqueioLoginService service;

    @BeforeEach
    void criar() {
        relogio = new RelogioAjustavel(Instant.parse("2026-01-10T12:00:00Z"));
        service = new BloqueioLoginService(props, relogio);
    }

    private void falhar(String email, String ip, int vezes) {
        for (int i = 0; i < vezes; i++) {
            service.registrarFalha(email, ip);
        }
    }

    private void assertLiberado(String email, String ip) {
        assertThatCode(() -> service.verificar(email, ip)).doesNotThrowAnyException();
    }

    private void assertBloqueado(String email, String ip) {
        assertThatThrownBy(() -> service.verificar(email, ip)).isInstanceOf(MuitasTentativasException.class);
    }

    @Test
    @DisplayName("quatro falhas não bloqueiam; a quinta bloqueia o par e-mail + IP")
    void limitePorEmailEIp() {
        falhar(EMAIL, IP, 4);
        assertLiberado(EMAIL, IP);

        falhar(EMAIL, IP, 1);
        assertBloqueado(EMAIL, IP);
    }

    @Test
    @DisplayName("o bloqueio dura 15 minutos: ainda vale um segundo antes, acaba no instante exato")
    void duracaoDoBloqueio() {
        falhar(EMAIL, IP, 5);

        relogio.avancar(Duration.ofMinutes(15).minusSeconds(1));
        assertThatThrownBy(() -> service.verificar(EMAIL, IP))
                .isInstanceOf(MuitasTentativasException.class)
                .hasMessage("Muitas tentativas. Tente novamente em 1 minuto.");

        relogio.avancar(Duration.ofSeconds(1));
        assertLiberado(EMAIL, IP);
    }

    @Test
    @DisplayName("bloqueio do par e-mail + IP não impede o mesmo e-mail em outro IP")
    void outroIpLiberado() {
        falhar(EMAIL, IP, 5);

        assertLiberado(EMAIL, "198.51.100.9");
    }

    @Test
    @DisplayName("e-mail é comparado sem diferenciar maiúsculas e espaços nas pontas")
    void emailNormalizado() {
        falhar(" ANA@Celke.uno ", IP, 5);

        assertBloqueado(EMAIL, IP);
    }

    @Test
    @DisplayName("vinte falhas espalhadas por vários IPs bloqueiam o e-mail em qualquer IP")
    void limitePorEmail() {
        for (int i = 0; i < 19; i++) {
            service.registrarFalha(EMAIL, "10.0.0." + i);
        }
        assertLiberado(EMAIL, "10.0.0.250");

        service.registrarFalha(EMAIL, "10.0.0.19");
        assertBloqueado(EMAIL, "10.0.0.250");
    }

    @Test
    @DisplayName("login correto zera os contadores")
    void sucessoZera() {
        falhar(EMAIL, IP, 4);

        service.registrarSucesso(EMAIL, IP);
        falhar(EMAIL, IP, 4);

        assertLiberado(EMAIL, IP);
    }

    @Test
    @DisplayName("falhas antigas deixam de contar depois de uma hora sem novas falhas")
    void janelaExpira() {
        falhar(EMAIL, IP, 4);

        relogio.avancar(Duration.ofHours(1));
        falhar(EMAIL, IP, 4);

        assertLiberado(EMAIL, IP);
    }

    @Test
    @DisplayName("falhas dentro da janela continuam somando")
    void janelaAinda() {
        falhar(EMAIL, IP, 4);

        relogio.avancar(Duration.ofHours(1).minusSeconds(1));
        falhar(EMAIL, IP, 1);

        assertBloqueado(EMAIL, IP);
    }

    @Test
    @DisplayName("depois do bloqueio, mais cinco falhas bloqueiam de novo")
    void bloqueiaDeNovo() {
        falhar(EMAIL, IP, 5);
        relogio.avancar(Duration.ofMinutes(15));

        falhar(EMAIL, IP, 4);
        assertLiberado(EMAIL, IP);
        falhar(EMAIL, IP, 1);
        assertBloqueado(EMAIL, IP);
    }
}
