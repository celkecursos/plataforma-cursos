package br.com.celke.cursos.matricula.application;

import static br.com.celke.cursos.support.fixtures.MatriculaFixture.matricula;
import static br.com.celke.cursos.support.fixtures.UsuarioFixture.inativo;
import static br.com.celke.cursos.support.fixtures.UsuarioFixture.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.TurmaResumo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.matricula.domain.FormaPagamento;
import br.com.celke.cursos.matricula.domain.Matricula;
import br.com.celke.cursos.matricula.domain.StatusMatricula;
import br.com.celke.cursos.matricula.infra.MatriculaRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class MatriculaAdmServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final String EMAIL = "ana@celke.uno";

    @Mock
    private MatriculaRepository matriculas;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private ConteudoTurmaService conteudoTurmaService;

    private MatriculaAdmService service;
    private final Usuario ana = usuario(7, EMAIL, "hash", Papel.ALUNO);

    @BeforeEach
    void setUp() {
        service = new MatriculaAdmService(
                matriculas, usuarioService, conteudoTurmaService, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static TurmaResumo turma(StatusTurma status) {
        return new TurmaResumo(5L, "Turma", status, 1L, "Java", "java", StatusCurso.PUBLICADO, null);
    }

    private static NovaMatricula nova(Instant inicio, Instant fim, String transacao) {
        return new NovaMatricula(EMAIL, inicio, fim, FormaPagamento.PIX, transacao);
    }

    @Nested
    class Matricular {

        @Test
        @DisplayName("sem início, começa agora; grava usuário, turma, forma de pagamento e transação")
        void matricula() {
            when(conteudoTurmaService.turma(5L)).thenReturn(Optional.of(turma(StatusTurma.ATIVA)));
            when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(Optional.of(ana));
            when(matriculas.existsByUsuarioIdAndTurmaIdAndStatus(7L, 5L, StatusMatricula.ATIVA))
                    .thenReturn(false);
            when(matriculas.existsByFormaPagamentoAndTransacao(FormaPagamento.PIX, "tx-1"))
                    .thenReturn(false);
            when(matriculas.saveAndFlush(any(Matricula.class))).thenAnswer(i -> i.getArgument(0));

            Instant fim = AGORA.plusSeconds(86_400);
            Matricula matricula =
                    service.matricular(5L, nova(null, fim, " tx-1 ")).matricula();

            assertThat(matricula.getUsuarioId()).isEqualTo(7L);
            assertThat(matricula.getTurmaId()).isEqualTo(5L);
            assertThat(matricula.getInicio()).isEqualTo(AGORA);
            assertThat(matricula.getFim()).isEqualTo(fim);
            assertThat(matricula.getFormaPagamento()).isEqualTo(FormaPagamento.PIX);
            assertThat(matricula.getTransacao()).isEqualTo("tx-1");
        }

        @Test
        @DisplayName("turma inexistente responde não encontrada")
        void turmaInexistente() {
            when(conteudoTurmaService.turma(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.matricular(9L, nova(null, null, null)))
                    .isInstanceOf(NaoEncontradoException.class)
                    .hasMessage("Turma não encontrada.");
            verify(matriculas, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("turma encerrada não recebe matrícula")
        void turmaEncerrada() {
            when(conteudoTurmaService.turma(5L)).thenReturn(Optional.of(turma(StatusTurma.ENCERRADA)));

            assertThatThrownBy(() -> service.matricular(5L, nova(null, null, null)))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("A turma está encerrada e não recebe novas matrículas.");
            verify(matriculas, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("usuário inexistente ou inativo responde não encontrado")
        void usuarioInexistenteOuInativo() {
            when(conteudoTurmaService.turma(5L)).thenReturn(Optional.of(turma(StatusTurma.PREPARACAO)));
            when(usuarioService.buscarPorEmail(EMAIL))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(inativo(usuario(8, EMAIL, "hash", Papel.ALUNO))));

            for (int i = 0; i < 2; i++) {
                assertThatThrownBy(() -> service.matricular(5L, nova(null, null, null)))
                        .isInstanceOf(NaoEncontradoException.class)
                        .hasMessage("Usuário não encontrado.");
            }
            verify(matriculas, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("aluno com matrícula ativa na turma recebe 409")
        void jaMatriculado() {
            when(conteudoTurmaService.turma(5L)).thenReturn(Optional.of(turma(StatusTurma.ATIVA)));
            when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(Optional.of(ana));
            when(matriculas.existsByUsuarioIdAndTurmaIdAndStatus(7L, 5L, StatusMatricula.ATIVA))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.matricular(5L, nova(null, null, null)))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("O aluno já tem matrícula nesta turma. Altere o período da matrícula atual.");
            verify(matriculas, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("transação já usada recebe 409")
        void transacaoRepetida() {
            when(conteudoTurmaService.turma(5L)).thenReturn(Optional.of(turma(StatusTurma.ATIVA)));
            when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(Optional.of(ana));
            when(matriculas.existsByUsuarioIdAndTurmaIdAndStatus(7L, 5L, StatusMatricula.ATIVA))
                    .thenReturn(false);
            when(matriculas.existsByFormaPagamentoAndTransacao(FormaPagamento.PIX, "tx-1"))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.matricular(5L, nova(null, null, "tx-1")))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("Esta transação já gerou uma matrícula.");
            verify(matriculas, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("duas matrículas iguais ao mesmo tempo: o índice do banco recusa a segunda com 409")
        void corrida() {
            when(conteudoTurmaService.turma(5L)).thenReturn(Optional.of(turma(StatusTurma.ATIVA)));
            when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(Optional.of(ana));
            when(matriculas.existsByUsuarioIdAndTurmaIdAndStatus(7L, 5L, StatusMatricula.ATIVA))
                    .thenReturn(false);
            when(matriculas.saveAndFlush(any(Matricula.class)))
                    .thenThrow(new DataIntegrityViolationException("uq_matriculas_ativa"));

            assertThatThrownBy(() -> service.matricular(5L, nova(null, null, null)))
                    .isInstanceOf(ConflitoException.class);
        }
    }

    @Test
    @DisplayName("lista as mais recentes primeiro, com os dados do aluno, ignorando a ordenação do cliente")
    void listar() {
        Matricula matricula = matricula(1, 7, 5, AGORA, null);
        var pagina = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"));
        when(conteudoTurmaService.turma(5L)).thenReturn(Optional.of(turma(StatusTurma.ATIVA)));
        when(matriculas.findByTurmaId(5L, pagina)).thenReturn(new PageImpl<>(List.of(matricula), pagina, 1));
        when(usuarioService.buscarPorIds(List.of(7L))).thenReturn(Map.of(7L, ana));

        var itens =
                service.listar(5L, PageRequest.of(0, 20, Sort.by("usuarioId"))).getContent();

        assertThat(itens).singleElement().satisfies(item -> {
            assertThat(item.matricula()).isSameAs(matricula);
            assertThat(item.usuario()).isSameAs(ana);
        });
    }

    @Test
    @DisplayName("altera o período e cancela; matrícula inexistente responde não encontrada")
    void periodoECancelamento() {
        Matricula matricula = matricula(1, 7, 5, AGORA, null);
        when(matriculas.findById(1L)).thenReturn(Optional.of(matricula));
        when(matriculas.findById(9L)).thenReturn(Optional.empty());
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(ana));

        Instant fim = AGORA.plusSeconds(3_600);
        service.alterarPeriodo(1L, AGORA, fim);
        assertThat(matricula.getFim()).isEqualTo(fim);
        service.cancelar(1L);
        assertThat(matricula.getStatus()).isEqualTo(StatusMatricula.CANCELADA);
        assertThatThrownBy(() -> service.cancelar(9L))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Matrícula não encontrada.");
    }
}
