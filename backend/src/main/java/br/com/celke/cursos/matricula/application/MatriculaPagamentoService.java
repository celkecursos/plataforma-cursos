package br.com.celke.cursos.matricula.application;

import br.com.celke.cursos.matricula.domain.FormaPagamento;
import br.com.celke.cursos.matricula.domain.Matricula;
import br.com.celke.cursos.matricula.domain.StatusMatricula;
import br.com.celke.cursos.matricula.infra.MatriculaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Matrícula pelo pagamento (módulo {@code pagamento}): conceder o acesso quando a compra é aprovada e
 * cancelar quando ela é reembolsada ou contestada. As duas operações podem ser repetidas sem efeito
 * duplicado (a mesma notificação pode ser processada de novo).
 */
@Service
public class MatriculaPagamentoService {

    private final MatriculaRepository matriculas;
    private final Clock clock;

    public MatriculaPagamentoService(MatriculaRepository matriculas, Clock clock) {
        this.matriculas = matriculas;
        this.clock = clock;
    }

    /**
     * Garante o acesso do comprador à turma. Ordem: a matrícula do próprio pedido ainda ativa não muda;
     * se o aluno já tem matrícula ativa na turma, ela é estendida; a do pedido, se foi cancelada, é
     * reativada; senão nasce uma nova a partir de {@code inicio}.
     */
    @Transactional
    public Concessao conceder(NovaConcessao dados) {
        Instant agora = clock.instant();
        Optional<Matricula> doPedido =
                Optional.ofNullable(dados.matriculaAtualId()).flatMap(matriculas::findById);
        if (doPedido.filter(m -> m.getStatus() == StatusMatricula.ATIVA).isPresent()) {
            return new Concessao(doPedido.get().getId(), false);
        }
        Optional<Matricula> ativaNaTurma = matriculas.findByUsuarioIdAndTurmaIdAndStatus(
                dados.usuarioId(), dados.turmaId(), StatusMatricula.ATIVA);
        if (ativaNaTurma.isPresent()) {
            Matricula matricula = ativaNaTurma.get();
            matricula.estender(dados.diasAcesso(), dados.inicio(), agora);
            return new Concessao(matricula.getId(), true);
        }
        if (doPedido.isPresent()) {
            doPedido.get().reativar(agora);
            return new Concessao(doPedido.get().getId(), true);
        }
        String transacao = dados.transacao();
        if (transacao != null && matriculas.existsByFormaPagamentoAndTransacao(dados.formaPagamento(), transacao)) {
            // A mesma transação já foi lançada à mão pelo ADM: a matrícula nova fica sem ela
            transacao = null;
        }
        Instant fim = dados.diasAcesso() == null ? null : dados.inicio().plus(Duration.ofDays(dados.diasAcesso()));
        Matricula nova = matriculas.saveAndFlush(Matricula.dePagamento(
                dados.usuarioId(),
                dados.turmaId(),
                dados.inicio(),
                fim,
                dados.formaPagamento(),
                transacao,
                dados.pedidoId(),
                agora));
        return new Concessao(nova.getId(), true);
    }

    /** Cancela a matrícula (reembolso, chargeback); já cancelada ou inexistente, nada muda. */
    @Transactional
    public void cancelar(Long matriculaId) {
        matriculas
                .findById(matriculaId)
                .filter(m -> m.getStatus() == StatusMatricula.ATIVA)
                .ifPresent(m -> m.cancelar(clock.instant()));
    }

    /** A matrícula existe e está ativa (vigente, futura ou vencida). */
    @Transactional(readOnly = true)
    public boolean ativa(Long matriculaId) {
        return matriculas
                .findById(matriculaId)
                .filter(m -> m.getStatus() == StatusMatricula.ATIVA)
                .isPresent();
    }

    /**
     * Dados da concessão.
     *
     * @param inicio início do acesso (a aprovação do pagamento)
     * @param diasAcesso dias de acesso da oferta; nulo, sem prazo
     * @param matriculaAtualId matrícula já ligada ao pedido, se houver
     */
    public record NovaConcessao(
            Long usuarioId,
            Long turmaId,
            Instant inicio,
            Integer diasAcesso,
            FormaPagamento formaPagamento,
            String transacao,
            Long pedidoId,
            Long matriculaAtualId) {}

    /**
     * @param novoAcesso o acesso foi aberto, reaberto ou estendido agora (vale avisar o aluno)
     */
    public record Concessao(Long matriculaId, boolean novoAcesso) {}
}
