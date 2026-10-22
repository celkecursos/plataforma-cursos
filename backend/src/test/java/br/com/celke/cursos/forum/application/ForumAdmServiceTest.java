package br.com.celke.cursos.forum.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.forum.application.LeituraForum.TopicoDetalhe;
import br.com.celke.cursos.forum.domain.Resposta;
import br.com.celke.cursos.forum.domain.Topico;
import br.com.celke.cursos.forum.infra.RespostaRepository;
import br.com.celke.cursos.forum.infra.TopicoRepository;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ForumAdmServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private TopicoRepository topicos;

    @Mock
    private RespostaRepository respostas;

    @Mock
    private ConteudoTurmaService conteudoTurmaService;

    @Mock
    private LeituraForum leitura;

    private ForumAdmService service;

    @BeforeEach
    void criar() {
        service = new ForumAdmService(
                topicos, respostas, conteudoTurmaService, leitura, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static Topico topico() {
        return new Topico(5L, null, 8L, "T", "C", AGORA.minusSeconds(60));
    }

    @Test
    @DisplayName("turma inexistente responde 404 na listagem")
    void turmaInexistente() {
        when(conteudoTurmaService.turma(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listar(5L, Pageable.ofSize(10)))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Turma não encontrada.");
        verify(topicos, never()).daTurma(any(), any());
    }

    @Test
    @DisplayName("moderar tópico aplica oculto e fixado e devolve a visão completa")
    void moderarTopico() {
        Topico topico = topico();
        when(topicos.findById(3L)).thenReturn(Optional.of(topico));
        when(leitura.detalhe(topico, true)).thenReturn(new TopicoDetalhe(topico, "Ana", List.of(), List.of()));

        service.moderarTopico(3L, true, true);

        assertThat(topico.isOculto()).isTrue();
        assertThat(topico.isFixado()).isTrue();
    }

    @Test
    @DisplayName("moderar resposta inexistente responde 404")
    void respostaInexistente() {
        when(respostas.findById(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.moderarResposta(4L, true))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Resposta não encontrada.");
    }

    @Test
    @DisplayName("moderar resposta oculta só ela")
    void moderarResposta() {
        Topico topico = topico();
        Resposta resposta = new Resposta(3L, 8L, "R", false, AGORA);
        when(respostas.findById(4L)).thenReturn(Optional.of(resposta));
        when(topicos.findById(3L)).thenReturn(Optional.of(topico));

        service.moderarResposta(4L, true);

        assertThat(resposta.isOculto()).isTrue();
        assertThat(topico.isOculto()).isFalse();
    }

    @Test
    @DisplayName("resposta do instrutor é marcada como tal e o tópico fica respondido")
    void responder() {
        Topico topico = topico();
        when(topicos.findById(3L)).thenReturn(Optional.of(topico));

        service.responder(1L, 3L, "Resposta oficial");

        ArgumentCaptor<Resposta> captor = ArgumentCaptor.forClass(Resposta.class);
        verify(respostas).save(captor.capture());
        assertThat(captor.getValue().isInstrutor()).isTrue();
        assertThat(captor.getValue().getAutorId()).isEqualTo(1L);
        assertThat(topico.isRespondidoInstrutor()).isTrue();
    }

    @Test
    @DisplayName("tópico inexistente responde 404")
    void topicoInexistente() {
        when(topicos.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.topico(3L))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Tópico não encontrado.");
    }
}
