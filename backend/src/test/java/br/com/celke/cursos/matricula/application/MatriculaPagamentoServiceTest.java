package br.com.celke.cursos.matricula.application;

import static br.com.celke.cursos.support.fixtures.MatriculaFixture.cancelada;
import static br.com.celke.cursos.support.fixtures.MatriculaFixture.matricula;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.matricula.application.MatriculaPagamentoService.Concessao;
import br.com.celke.cursos.matricula.application.MatriculaPagamentoService.NovaConcessao;
import br.com.celke.cursos.matricula.domain.FormaPagamento;
import br.com.celke.cursos.matricula.domain.Matricula;
import br.com.celke.cursos.matricula.domain.StatusMatricula;
import br.com.celke.cursos.matricula.infra.MatriculaRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MatriculaPagamentoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final Instant APROVACAO = Instant.parse("2026-03-10T11:00:00Z");

    @Mock
    private MatriculaRepository matriculas;

    private MatriculaPagamentoService service;

    @BeforeEach
    void setUp() {
        service = new MatriculaPagamentoService(matriculas, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static NovaConcessao concessao(Integer dias, Long matriculaAtual) {
        return new NovaConcessao(7L, 3L, APROVACAO, dias, FormaPagamento.CARTAO, "HP1", 9L, matriculaAtual);
    }

    @Nested
    class Conceder {

        @Test
        @DisplayName("sem matrícula: cria a do pagamento, com o fim pelos dias de acesso")
        void cria() {
            when(matriculas.findByUsuarioIdAndTurmaIdAndStatus(7L, 3L, StatusMatricula.ATIVA))
                    .thenReturn(Optional.empty());
            when(matriculas.existsByFormaPagamentoAndTransacao(FormaPagamento.CARTAO, "HP1"))
                    .thenReturn(false);
            when(matriculas.saveAndFlush(any())).thenAnswer(chamada -> {
                Matricula m = chamada.getArgument(0);
                ReflectionTestUtils.setField(m, "id", 50L);
                return m;
            });

            Concessao resultado = service.conceder(concessao(365, null));

            assertThat(resultado).isEqualTo(new Concessao(50L, true));
            ArgumentCaptor<Matricula> gravada = ArgumentCaptor.forClass(Matricula.class);
            verify(matriculas).saveAndFlush(gravada.capture());
            assertThat(gravada.getValue().getInicio()).isEqualTo(APROVACAO);
            assertThat(gravada.getValue().getFim()).isEqualTo(APROVACAO.plus(Duration.ofDays(365)));
            assertThat(gravada.getValue().getPedidoId()).isEqualTo(9L);
            assertThat(gravada.getValue().getTransacao()).isEqualTo("HP1");
        }

        @Test
        @DisplayName("sem dias de acesso: sem prazo; transação já lançada à mão fica fora da matrícula nova")
        void semPrazoETransacaoRepetida() {
            when(matriculas.findByUsuarioIdAndTurmaIdAndStatus(7L, 3L, StatusMatricula.ATIVA))
                    .thenReturn(Optional.empty());
            when(matriculas.existsByFormaPagamentoAndTransacao(FormaPagamento.CARTAO, "HP1"))
                    .thenReturn(true);
            when(matriculas.saveAndFlush(any())).thenAnswer(chamada -> chamada.getArgument(0));

            service.conceder(concessao(null, null));

            ArgumentCaptor<Matricula> gravada = ArgumentCaptor.forClass(Matricula.class);
            verify(matriculas).saveAndFlush(gravada.capture());
            assertThat(gravada.getValue().getFim()).isNull();
            assertThat(gravada.getValue().getTransacao()).isNull();
        }

        @Test
        @DisplayName("a matrícula do pedido ainda ativa não muda (evento repetido)")
        void jaConcedida() {
            when(matriculas.findById(50L)).thenReturn(Optional.of(matricula(50, 7, 3, APROVACAO, null)));

            assertThat(service.conceder(concessao(30, 50L))).isEqualTo(new Concessao(50L, false));
            verify(matriculas, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("aluno já matriculado na turma: estende o período em vez de duplicar")
        void estende() {
            Matricula atual =
                    matricula(40, 7, 3, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-04-01T00:00:00Z"));
            when(matriculas.findByUsuarioIdAndTurmaIdAndStatus(7L, 3L, StatusMatricula.ATIVA))
                    .thenReturn(Optional.of(atual));

            assertThat(service.conceder(concessao(30, null))).isEqualTo(new Concessao(40L, true));
            assertThat(atual.getFim()).isEqualTo(Instant.parse("2026-05-01T00:00:00Z"));
            verify(matriculas, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("matrícula do pedido cancelada (disputa resolvida): reativa")
        void reativa() {
            Matricula doPedido = cancelada(matricula(50, 7, 3, APROVACAO, null));
            when(matriculas.findById(50L)).thenReturn(Optional.of(doPedido));
            when(matriculas.findByUsuarioIdAndTurmaIdAndStatus(7L, 3L, StatusMatricula.ATIVA))
                    .thenReturn(Optional.empty());

            assertThat(service.conceder(concessao(30, 50L))).isEqualTo(new Concessao(50L, true));
            assertThat(doPedido.getStatus()).isEqualTo(StatusMatricula.ATIVA);
        }
    }

    @Test
    @DisplayName("cancelar: ativa é cancelada; cancelada ou inexistente não muda nada")
    void cancelar() {
        Matricula ativa = matricula(50, 7, 3, APROVACAO, null);
        when(matriculas.findById(50L)).thenReturn(Optional.of(ativa));
        when(matriculas.findById(51L)).thenReturn(Optional.empty());

        service.cancelar(50L);
        service.cancelar(50L);
        service.cancelar(51L);

        assertThat(ativa.getStatus()).isEqualTo(StatusMatricula.CANCELADA);
        assertThat(ativa.getCanceladaEm()).isEqualTo(AGORA);
    }

    @Test
    @DisplayName("ativa: só a matrícula existente e não cancelada")
    void ativa() {
        when(matriculas.findById(50L)).thenReturn(Optional.of(matricula(50, 7, 3, APROVACAO, null)));
        when(matriculas.findById(51L)).thenReturn(Optional.of(cancelada(matricula(51, 7, 3, APROVACAO, null))));
        when(matriculas.findById(52L)).thenReturn(Optional.empty());

        assertThat(service.ativa(50L)).isTrue();
        assertThat(service.ativa(51L)).isFalse();
        assertThat(service.ativa(52L)).isFalse();
    }
}
