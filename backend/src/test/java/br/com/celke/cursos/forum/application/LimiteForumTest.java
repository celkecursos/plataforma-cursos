package br.com.celke.cursos.forum.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.forum.domain.Resposta;
import br.com.celke.cursos.forum.domain.Topico;
import br.com.celke.cursos.forum.infra.RespostaRepository;
import br.com.celke.cursos.forum.infra.TopicoRepository;
import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LimiteForumTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final Instant DESDE = AGORA.minus(Duration.ofHours(1));

    @Mock
    private TopicoRepository topicos;

    @Mock
    private RespostaRepository respostas;

    private LimiteForum limite;

    @BeforeEach
    void criar() {
        limite = new LimiteForum(
                topicos, respostas, new ForumProperties(5, Duration.ofHours(1), 3), Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private void publicadas(long topicosNaJanela, long respostasNaJanela) {
        when(topicos.countByAutorIdAndCriadoEmAfter(7L, DESDE)).thenReturn(topicosNaJanela);
        when(respostas.countByAutorIdAndCriadoEmAfter(7L, DESDE)).thenReturn(respostasNaJanela);
    }

    @ParameterizedTest(name = "{0} tópicos + {1} respostas")
    @CsvSource({"0, 0", "4, 0", "0, 4", "2, 2"})
    @DisplayName("abaixo de 5 mensagens na última hora, publica")
    void abaixoDoLimite(long t, long r) {
        publicadas(t, r);

        limite.exigirDentroDoLimite(7L);
    }

    @ParameterizedTest(name = "{0} tópicos + {1} respostas")
    @CsvSource({"5, 0", "3, 2", "0, 9"})
    @DisplayName("no limite (tópicos e respostas somados): 429 com a espera até a mais antiga sair da janela")
    void noLimite(long t, long r) {
        publicadas(t, r);
        Topico antigo = new Topico(5L, null, 7L, "T", "C", AGORA.minus(Duration.ofMinutes(50)));
        Resposta maisAntiga = new Resposta(1L, 7L, "C", false, AGORA.minus(Duration.ofMinutes(55)));
        when(topicos.findFirstByAutorIdAndCriadoEmAfterOrderByCriadoEm(7L, DESDE))
                .thenReturn(Optional.of(antigo));
        when(respostas.findFirstByAutorIdAndCriadoEmAfterOrderByCriadoEm(7L, DESDE))
                .thenReturn(Optional.of(maisAntiga));

        assertThatThrownBy(() -> limite.exigirDentroDoLimite(7L))
                .isInstanceOfSatisfying(
                        MuitasTentativasException.class,
                        e -> assertThat(e.getEspera()).isEqualTo(Duration.ofMinutes(5)))
                .hasMessage("Muitas tentativas. Tente novamente em 5 minutos.");
    }
}
