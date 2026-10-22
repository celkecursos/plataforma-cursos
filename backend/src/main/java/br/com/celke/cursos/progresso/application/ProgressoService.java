package br.com.celke.cursos.progresso.application;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.AulaConteudo;
import br.com.celke.cursos.matricula.application.AcessoAulaService;
import br.com.celke.cursos.progresso.domain.PercentualConclusao;
import br.com.celke.cursos.progresso.domain.ProgressoAula;
import br.com.celke.cursos.progresso.infra.ProgressoAulaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Progresso do aluno: posição no vídeo, aula vista e marcada para revisar, e o percentual da turma.
 * Sempre no escopo do usuário do token e pela {@link AcessoAulaService}: sem acesso à aula, não há
 * progresso para ler nem gravar.
 */
@Service
public class ProgressoService {

    private final ProgressoAulaRepository progressos;
    private final AcessoAulaService acessoAulaService;
    private final ConteudoTurmaService conteudoTurmaService;
    private final TransactionTemplate transacao;
    private final Clock clock;

    public ProgressoService(
            ProgressoAulaRepository progressos,
            AcessoAulaService acessoAulaService,
            ConteudoTurmaService conteudoTurmaService,
            TransactionTemplate transacao,
            Clock clock) {
        this.progressos = progressos;
        this.acessoAulaService = acessoAulaService;
        this.conteudoTurmaService = conteudoTurmaService;
        this.transacao = transacao;
        this.clock = clock;
    }

    /** Progresso na aula; aula nunca aberta volta zerada. */
    @Transactional(readOnly = true)
    public Progresso daAula(Long usuarioId, Long aulaId) {
        acessoAulaService.exigirAcesso(usuarioId, aulaId);
        return progressos
                .findByUsuarioIdAndAulaId(usuarioId, aulaId)
                .map(Progresso::de)
                .orElse(Progresso.vazio(aulaId));
    }

    /**
     * Grava os campos preenchidos (os nulos ficam como estão). O primeiro registro da aula pode
     * chegar em duas requisições ao mesmo tempo (duas abas): a restrição única recusa uma, que é
     * refeita sobre o registro que ficou.
     */
    public Progresso atualizar(Long usuarioId, Long aulaId, Integer posicaoSegundos, Boolean vista, Boolean revisar) {
        acessoAulaService.exigirAcesso(usuarioId, aulaId);
        try {
            return gravar(usuarioId, aulaId, posicaoSegundos, vista, revisar);
        } catch (DataIntegrityViolationException e) {
            return gravar(usuarioId, aulaId, posicaoSegundos, vista, revisar);
        }
    }

    private Progresso gravar(Long usuarioId, Long aulaId, Integer posicaoSegundos, Boolean vista, Boolean revisar) {
        return transacao.execute(status -> {
            Instant agora = clock.instant();
            ProgressoAula progresso = progressos
                    .findByUsuarioIdAndAulaId(usuarioId, aulaId)
                    .orElseGet(() -> new ProgressoAula(usuarioId, aulaId, agora));
            progresso.atualizar(posicaoSegundos, vista, revisar, agora);
            return Progresso.de(progressos.saveAndFlush(progresso));
        });
    }

    /** Percentual de aulas vistas entre as aulas ativas da turma, com o progresso de cada uma. */
    @Transactional(readOnly = true)
    public ProgressoTurma daTurma(Long usuarioId, Long turmaId) {
        acessoAulaService.exigirAcessoTurma(usuarioId, turmaId);
        List<Long> aulaIds = conteudoTurmaService.conteudoAtivo(turmaId).stream()
                .flatMap(modulo -> modulo.aulas().stream())
                .map(AulaConteudo::id)
                .toList();
        Map<Long, ProgressoAula> porAula = aulaIds.isEmpty()
                ? Map.of()
                : progressos.findByUsuarioIdAndAulaIdIn(usuarioId, aulaIds).stream()
                        .collect(Collectors.toMap(ProgressoAula::getAulaId, Function.identity()));
        List<Progresso> aulas = aulaIds.stream()
                .map(id -> porAula.containsKey(id) ? Progresso.de(porAula.get(id)) : Progresso.vazio(id))
                .toList();
        int vistas = (int) aulas.stream().filter(Progresso::vista).count();
        return new ProgressoTurma(turmaId, aulas.size(), vistas, PercentualConclusao.de(vistas, aulas.size()), aulas);
    }

    public record Progresso(Long aulaId, int posicaoSegundos, boolean vista, Instant vistaEm, boolean revisar) {

        static Progresso de(ProgressoAula p) {
            return new Progresso(p.getAulaId(), p.getPosicaoSegundos(), p.isVista(), p.getVistaEm(), p.isRevisar());
        }

        static Progresso vazio(Long aulaId) {
            return new Progresso(aulaId, 0, false, null, false);
        }
    }

    /** Aulas na ordem do curso (módulos e aulas ativos). */
    public record ProgressoTurma(
            Long turmaId, int totalAulas, int aulasVistas, int percentual, List<Progresso> aulas) {}
}
