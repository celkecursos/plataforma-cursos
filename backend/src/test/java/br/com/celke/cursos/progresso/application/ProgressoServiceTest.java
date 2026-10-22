package br.com.celke.cursos.progresso.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.AulaConteudo;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.ModuloConteudo;
import br.com.celke.cursos.matricula.application.AcessoAulaService;
import br.com.celke.cursos.progresso.application.ProgressoService.Progresso;
import br.com.celke.cursos.progresso.domain.ProgressoAula;
import br.com.celke.cursos.progresso.infra.ProgressoAulaRepository;
import br.com.celke.cursos.shared.erro.AcessoNegadoException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ProgressoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private ProgressoAulaRepository progressos;

    @Mock
    private AcessoAulaService acessoAulaService;

    @Mock
    private ConteudoTurmaService conteudoTurmaService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ProgressoService service;

    @BeforeEach
    void criar() {
        service = new ProgressoService(
                progressos,
                acessoAulaService,
                conteudoTurmaService,
                new TransactionTemplate(transactionManager),
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private void transacoes() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private static ProgressoAula progresso(long aulaId, int posicao, boolean vista) {
        ProgressoAula p = new ProgressoAula(7L, aulaId, AGORA);
        p.atualizar(posicao, vista, null, AGORA);
        return p;
    }

    @Nested
    class DaAula {

        @Test
        @DisplayName("aula nunca aberta volta zerada")
        void zerada() {
            when(progressos.findByUsuarioIdAndAulaId(7L, 9L)).thenReturn(Optional.empty());

            assertThat(service.daAula(7L, 9L)).isEqualTo(new Progresso(9L, 0, false, null, false));
            verify(acessoAulaService).exigirAcesso(7L, 9L);
        }

        @Test
        @DisplayName("devolve o progresso gravado")
        void gravado() {
            when(progressos.findByUsuarioIdAndAulaId(7L, 9L)).thenReturn(Optional.of(progresso(9L, 95, true)));

            assertThat(service.daAula(7L, 9L)).isEqualTo(new Progresso(9L, 95, true, AGORA, false));
        }

        @Test
        @DisplayName("sem acesso à aula, nada é lido")
        void semAcesso() {
            doThrow(new AcessoNegadoException("sem acesso"))
                    .when(acessoAulaService)
                    .exigirAcesso(7L, 9L);

            assertThatThrownBy(() -> service.daAula(7L, 9L)).isInstanceOf(AcessoNegadoException.class);
            verify(progressos, never()).findByUsuarioIdAndAulaId(any(), any());
        }
    }

    @Nested
    class Atualizar {

        @Test
        @DisplayName("primeiro registro da aula é criado com os campos enviados")
        void cria() {
            transacoes();
            when(progressos.findByUsuarioIdAndAulaId(7L, 9L)).thenReturn(Optional.empty());
            when(progressos.saveAndFlush(any(ProgressoAula.class))).thenAnswer(i -> i.getArgument(0));

            assertThat(service.atualizar(7L, 9L, 42, null, true)).isEqualTo(new Progresso(9L, 42, false, null, true));
        }

        @Test
        @DisplayName("registro existente muda só nos campos enviados")
        void atualiza() {
            transacoes();
            when(progressos.findByUsuarioIdAndAulaId(7L, 9L)).thenReturn(Optional.of(progresso(9L, 95, false)));
            when(progressos.saveAndFlush(any(ProgressoAula.class))).thenAnswer(i -> i.getArgument(0));

            assertThat(service.atualizar(7L, 9L, null, true, null))
                    .isEqualTo(new Progresso(9L, 95, true, AGORA, false));
        }

        @Test
        @DisplayName("duas abas criando ao mesmo tempo: a segunda é refeita sobre o registro que ficou")
        void corrida() {
            transacoes();
            ProgressoAula existente = progresso(9L, 10, false);
            when(progressos.findByUsuarioIdAndAulaId(7L, 9L))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(existente));
            when(progressos.saveAndFlush(any(ProgressoAula.class)))
                    .thenThrow(new DataIntegrityViolationException("uq_progresso_aulas"))
                    .thenAnswer(i -> i.getArgument(0));

            assertThat(service.atualizar(7L, 9L, 60, null, null).posicaoSegundos())
                    .isEqualTo(60);
            assertThat(existente.getPosicaoSegundos()).isEqualTo(60);
        }

        @Test
        @DisplayName("sem acesso à aula, nada é gravado")
        void semAcesso() {
            doThrow(new AcessoNegadoException("sem acesso"))
                    .when(acessoAulaService)
                    .exigirAcesso(7L, 9L);

            assertThatThrownBy(() -> service.atualizar(7L, 9L, 10, null, null))
                    .isInstanceOf(AcessoNegadoException.class);
            verify(progressos, never()).saveAndFlush(any());
        }
    }

    @Nested
    class DaTurma {

        private void conteudo(List<ModuloConteudo> modulos) {
            when(conteudoTurmaService.conteudoAtivo(5L)).thenReturn(modulos);
        }

        @Test
        @DisplayName("percentual das aulas ativas vistas, com cada aula na ordem do curso")
        void percentual() {
            conteudo(List.of(
                    new ModuloConteudo(
                            1L, "M1", List.of(new AulaConteudo(10L, "A", false), new AulaConteudo(11L, "B", false))),
                    new ModuloConteudo(2L, "M2", List.of(new AulaConteudo(12L, "C", true)))));
            when(progressos.findByUsuarioIdAndAulaIdIn(7L, List.of(10L, 11L, 12L)))
                    .thenReturn(List.of(progresso(12L, 30, true), progresso(10L, 5, false)));

            var turma = service.daTurma(7L, 5L);

            verify(acessoAulaService).exigirAcessoTurma(7L, 5L);
            assertThat(turma.totalAulas()).isEqualTo(3);
            assertThat(turma.aulasVistas()).isEqualTo(1);
            assertThat(turma.percentual()).isEqualTo(33);
            assertThat(turma.aulas()).extracting(Progresso::aulaId).containsExactly(10L, 11L, 12L);
            assertThat(turma.aulas().get(1)).isEqualTo(new Progresso(11L, 0, false, null, false));
        }

        @Test
        @DisplayName("todas as aulas vistas: 100%")
        void todas() {
            conteudo(List.of(new ModuloConteudo(1L, "M1", List.of(new AulaConteudo(10L, "A", false)))));
            when(progressos.findByUsuarioIdAndAulaIdIn(7L, List.of(10L))).thenReturn(List.of(progresso(10L, 0, true)));

            assertThat(service.daTurma(7L, 5L).percentual()).isEqualTo(100);
        }

        @Test
        @DisplayName("turma sem aulas: 0%, sem consultar o progresso")
        void semAulas() {
            conteudo(List.of(new ModuloConteudo(1L, "Vazio", List.of())));

            var turma = service.daTurma(7L, 5L);

            assertThat(turma.percentual()).isZero();
            assertThat(turma.totalAulas()).isZero();
            assertThat(turma.aulas()).isEmpty();
            verify(progressos, never()).findByUsuarioIdAndAulaIdIn(any(), any());
        }

        @Test
        @DisplayName("sem acesso à turma, nada é lido")
        void semAcesso() {
            doThrow(new AcessoNegadoException("sem acesso"))
                    .when(acessoAulaService)
                    .exigirAcessoTurma(7L, 5L);

            assertThatThrownBy(() -> service.daTurma(7L, 5L)).isInstanceOf(AcessoNegadoException.class);
            verify(conteudoTurmaService, never()).conteudoAtivo(any());
        }
    }
}
