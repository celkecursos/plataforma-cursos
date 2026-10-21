package br.com.celke.cursos.matricula.application;

import static br.com.celke.cursos.support.fixtures.MatriculaFixture.matricula;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.AulaContexto;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.matricula.domain.AcessoAulaPolicy;
import br.com.celke.cursos.matricula.domain.StatusMatricula;
import br.com.celke.cursos.matricula.infra.MatriculaRepository;
import br.com.celke.cursos.shared.erro.AcessoNegadoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AcessoAulaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private ConteudoTurmaService conteudoTurmaService;

    @Mock
    private MatriculaRepository matriculas;

    private AcessoAulaService service;

    @BeforeEach
    void setUp() {
        service = new AcessoAulaService(
                conteudoTurmaService, matriculas, new AcessoAulaPolicy(Clock.fixed(AGORA, ZoneOffset.UTC)));
    }

    private static AulaContexto aula(boolean gratuita) {
        return new AulaContexto(100L, "Aula", null, gratuita, 5L, StatusTurma.ATIVA, StatusCurso.PUBLICADO);
    }

    @Test
    @DisplayName("matrícula vigente na turma da aula libera o acesso")
    void comMatricula() {
        when(conteudoTurmaService.aulaAtiva(100L)).thenReturn(Optional.of(aula(false)));
        when(matriculas.findByUsuarioIdAndTurmaIdAndStatus(7L, 5L, StatusMatricula.ATIVA))
                .thenReturn(Optional.of(matricula(1, 7, 5, AGORA.minusSeconds(60), null)));

        assertThat(service.exigirAcesso(7L, 100L).id()).isEqualTo(100L);
    }

    @Test
    @DisplayName("aula aberta libera sem matrícula")
    void aulaAberta() {
        when(conteudoTurmaService.aulaAtiva(100L)).thenReturn(Optional.of(aula(true)));
        when(matriculas.findByUsuarioIdAndTurmaIdAndStatus(7L, 5L, StatusMatricula.ATIVA))
                .thenReturn(Optional.empty());

        assertThat(service.exigirAcesso(7L, 100L).gratuita()).isTrue();
    }

    @Test
    @DisplayName("sem matrícula vigente e aula paga responde 403")
    void semAcesso() {
        when(conteudoTurmaService.aulaAtiva(100L)).thenReturn(Optional.of(aula(false)));
        when(matriculas.findByUsuarioIdAndTurmaIdAndStatus(7L, 5L, StatusMatricula.ATIVA))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exigirAcesso(7L, 100L))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessage("Você não tem acesso a esta aula. Verifique o período da sua matrícula.");
    }

    @Test
    @DisplayName("aula inexistente ou inativa responde 404 sem consultar a matrícula")
    void aulaInexistente() {
        when(conteudoTurmaService.aulaAtiva(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exigirAcesso(7L, 9L))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Aula não encontrada.");
        verify(matriculas, never()).findByUsuarioIdAndTurmaIdAndStatus(any(), any(), any());
    }
}
