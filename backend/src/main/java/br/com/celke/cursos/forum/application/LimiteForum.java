package br.com.celke.cursos.forum.application;

import br.com.celke.cursos.forum.domain.Resposta;
import br.com.celke.cursos.forum.domain.Topico;
import br.com.celke.cursos.forum.infra.RespostaRepository;
import br.com.celke.cursos.forum.infra.TopicoRepository;
import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Limite de perguntas e respostas por aluno numa janela de tempo, contado no banco (vale para todas as
 * instâncias da API). Passou do limite: 429 com o tempo até a mensagem mais antiga da janela sair dela.
 */
@Component
public class LimiteForum {

    private final TopicoRepository topicos;
    private final RespostaRepository respostas;
    private final ForumProperties props;
    private final Clock clock;

    public LimiteForum(TopicoRepository topicos, RespostaRepository respostas, ForumProperties props, Clock clock) {
        this.topicos = topicos;
        this.respostas = respostas;
        this.props = props;
        this.clock = clock;
    }

    public void exigirDentroDoLimite(Long usuarioId) {
        Instant agora = clock.instant();
        Instant desde = agora.minus(props.janela());
        long publicadas = topicos.countByAutorIdAndCriadoEmAfter(usuarioId, desde)
                + respostas.countByAutorIdAndCriadoEmAfter(usuarioId, desde);
        if (publicadas < props.mensagensPorJanela()) {
            return;
        }
        Instant maisAntiga = Stream.concat(
                        topicos.findFirstByAutorIdAndCriadoEmAfterOrderByCriadoEm(usuarioId, desde).stream()
                                .map(Topico::getCriadoEm),
                        respostas.findFirstByAutorIdAndCriadoEmAfterOrderByCriadoEm(usuarioId, desde).stream()
                                .map(Resposta::getCriadoEm))
                .min(Instant::compareTo)
                .orElse(agora);
        throw new MuitasTentativasException(Duration.between(agora, maisAntiga.plus(props.janela())));
    }
}
