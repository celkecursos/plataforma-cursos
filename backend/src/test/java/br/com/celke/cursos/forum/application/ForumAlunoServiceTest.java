package br.com.celke.cursos.forum.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.AulaContexto;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.forum.application.AnexoForumService.AnexoGravado;
import br.com.celke.cursos.forum.application.LeituraForum.TopicoDetalhe;
import br.com.celke.cursos.forum.domain.Anexo;
import br.com.celke.cursos.forum.domain.Resposta;
import br.com.celke.cursos.forum.domain.Topico;
import br.com.celke.cursos.forum.infra.AnexoRepository;
import br.com.celke.cursos.forum.infra.RespostaRepository;
import br.com.celke.cursos.forum.infra.TopicoRepository;
import br.com.celke.cursos.matricula.application.AcessoAulaService;
import br.com.celke.cursos.shared.erro.AcessoNegadoException;
import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ForumAlunoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final List<AnexoGravado> GRAVADOS = List.of(new AnexoGravado("forum/anexos/a.jpg", 10));

    @Mock
    private TopicoRepository topicos;

    @Mock
    private RespostaRepository respostas;

    @Mock
    private AnexoRepository anexos;

    @Mock
    private AcessoAulaService acessoAulaService;

    @Mock
    private ConteudoTurmaService conteudoTurmaService;

    @Mock
    private AnexoForumService anexoForumService;

    @Mock
    private LimiteForum limite;

    @Mock
    private LeituraForum leitura;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ForumAlunoService service;

    @BeforeEach
    void criar() {
        service = new ForumAlunoService(
                topicos,
                respostas,
                anexos,
                acessoAulaService,
                conteudoTurmaService,
                anexoForumService,
                limite,
                leitura,
                new TransactionTemplate(transactionManager),
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private void transacoes() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private static AulaContexto aula(long turmaId) {
        return new AulaContexto(9L, "Aula", null, false, turmaId, StatusTurma.ATIVA, StatusCurso.PUBLICADO);
    }

    private static TopicoDetalhe detalhe(Topico topico) {
        return new TopicoDetalhe(topico, "Ana", List.of(), List.of());
    }

    @Test
    @DisplayName("pergunta numa aula da turma: confere acesso e limite, grava tópico e anexos")
    void perguntar() {
        transacoes();
        when(conteudoTurmaService.aulaAtiva(9L)).thenReturn(Optional.of(aula(5L)));
        when(anexoForumService.processar(7L, List.of("k"))).thenReturn(GRAVADOS);
        when(topicos.save(any(Topico.class))).thenAnswer(i -> i.getArgument(0));
        when(leitura.detalhe(any(Topico.class), eq(false))).thenAnswer(i -> detalhe(i.getArgument(0)));

        TopicoDetalhe d = service.perguntar(7L, 5L, 9L, "Dúvida", "Corpo", List.of("k"));

        verify(acessoAulaService).exigirAcessoTurma(7L, 5L);
        verify(limite).exigirDentroDoLimite(7L);
        verify(anexos).save(any(Anexo.class));
        assertThat(d.topico().getTurmaId()).isEqualTo(5L);
        assertThat(d.topico().getAulaId()).isEqualTo(9L);
        assertThat(d.topico().getAutorId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("aula de outra turma (ou inexistente) responde 404 antes do limite e dos anexos")
    void aulaDeOutraTurma() {
        when(conteudoTurmaService.aulaAtiva(9L)).thenReturn(Optional.of(aula(6L)));

        assertThatThrownBy(() -> service.perguntar(7L, 5L, 9L, "T", "C", List.of("k")))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Aula não encontrada.");
        verify(limite, never()).exigirDentroDoLimite(any());
        verify(anexoForumService, never()).processar(any(), anyList());
    }

    @Test
    @DisplayName("sem matrícula vigente: nada é gravado")
    void semAcesso() {
        doThrow(new AcessoNegadoException("sem acesso")).when(acessoAulaService).exigirAcessoTurma(7L, 5L);

        assertThatThrownBy(() -> service.perguntar(7L, 5L, null, "T", "C", null))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.listar(7L, 5L, null, org.springframework.data.domain.Pageable.ofSize(10)))
                .isInstanceOf(AcessoNegadoException.class);
        verify(topicos, never()).save(any());
    }

    @Test
    @DisplayName("limite atingido: 429 sem processar anexos")
    void limiteAtingido() {
        doThrow(new MuitasTentativasException(Duration.ofMinutes(5)))
                .when(limite)
                .exigirDentroDoLimite(7L);

        assertThatThrownBy(() -> service.perguntar(7L, 5L, null, "T", "C", List.of("k")))
                .isInstanceOf(MuitasTentativasException.class);
        verify(anexoForumService, never()).processar(any(), anyList());
    }

    @Test
    @DisplayName("falha ao gravar a pergunta descarta as imagens já processadas")
    void falhaDescartaAnexos() {
        transacoes();
        when(anexoForumService.processar(7L, List.of("k"))).thenReturn(GRAVADOS);
        when(topicos.save(any(Topico.class))).thenThrow(new IllegalStateException("banco"));

        assertThatThrownBy(() -> service.perguntar(7L, 5L, null, "T", "C", List.of("k")))
                .isInstanceOf(IllegalStateException.class);
        verify(anexoForumService).descartar(GRAVADOS);
    }

    @Test
    @DisplayName("tópico oculto ou inexistente responde 404 para o aluno, na leitura e na resposta")
    void ocultoOuInexistente() {
        when(topicos.findByIdAndOcultoFalse(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.topico(7L, 3L))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Tópico não encontrado.");
        assertThatThrownBy(() -> service.responder(7L, 3L, "C", null)).isInstanceOf(NaoEncontradoException.class);
        verify(respostas, never()).save(any());
    }

    @Test
    @DisplayName("resposta de aluno: confere a turma do tópico e o limite; não é de instrutor")
    void responder() {
        transacoes();
        Topico topico = new Topico(5L, null, 8L, "T", "C", AGORA);
        when(topicos.findByIdAndOcultoFalse(3L)).thenReturn(Optional.of(topico));
        when(anexoForumService.processar(7L, null)).thenReturn(List.of());
        when(respostas.save(any(Resposta.class))).thenAnswer(i -> i.getArgument(0));
        when(leitura.detalhe(topico, false)).thenReturn(detalhe(topico));

        service.responder(7L, 3L, "Resposta", null);

        verify(acessoAulaService).exigirAcessoTurma(7L, 5L);
        verify(limite).exigirDentroDoLimite(7L);
        verify(respostas).save(org.mockito.ArgumentMatchers.argThat(r -> !r.isInstrutor() && r.getAutorId() == 7L));
        assertThat(topico.isRespondidoInstrutor()).isFalse();
    }
}
