package br.com.celke.cursos.pagamento.domain;

import static br.com.celke.cursos.pagamento.domain.StatusPedido.AGUARDANDO_PAGAMENTO;
import static br.com.celke.cursos.pagamento.domain.StatusPedido.APROVADO;
import static br.com.celke.cursos.pagamento.domain.StatusPedido.ATRASADO;
import static br.com.celke.cursos.pagamento.domain.StatusPedido.CANCELADO;
import static br.com.celke.cursos.pagamento.domain.StatusPedido.CHARGEBACK;
import static br.com.celke.cursos.pagamento.domain.StatusPedido.CONCLUIDO;
import static br.com.celke.cursos.pagamento.domain.StatusPedido.CRIADO;
import static br.com.celke.cursos.pagamento.domain.StatusPedido.EM_DISPUTA;
import static br.com.celke.cursos.pagamento.domain.StatusPedido.EXPIRADO;
import static br.com.celke.cursos.pagamento.domain.StatusPedido.REEMBOLSADO;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Todas as mudanças de status do pedido e o efeito de cada uma na matrícula. Os meios de pagamento
 * mandam eventos fora de ordem e reenviam antigos, por isso:
 *
 * <ul>
 *   <li>só as transições da tabela {@link #ORIGENS} valem (ex.: {@code APROVADO} depois de
 *       {@code REEMBOLSADO} não reabre o acesso);
 *   <li>um evento mais antigo que o da última mudança é ignorado, exceto reembolso e chargeback, que
 *       sempre valem.
 * </ul>
 */
public final class TransicoesPedido {

    /** Para cada status de destino, de quais status o pedido pode chegar nele. */
    static final Map<StatusPedido, Set<StatusPedido>> ORIGENS = new EnumMap<>(StatusPedido.class);

    /** Status que valem mesmo chegando fora de ordem. */
    static final Set<StatusPedido> SEMPRE_VALEM = EnumSet.of(REEMBOLSADO, CHARGEBACK);

    static {
        ORIGENS.put(CRIADO, EnumSet.noneOf(StatusPedido.class));
        ORIGENS.put(AGUARDANDO_PAGAMENTO, EnumSet.of(CRIADO));
        ORIGENS.put(APROVADO, EnumSet.of(CRIADO, AGUARDANDO_PAGAMENTO, EXPIRADO, ATRASADO, EM_DISPUTA));
        ORIGENS.put(CONCLUIDO, EnumSet.of(CRIADO, AGUARDANDO_PAGAMENTO, APROVADO, ATRASADO, EM_DISPUTA));
        ORIGENS.put(EM_DISPUTA, EnumSet.of(APROVADO, CONCLUIDO, ATRASADO));
        ORIGENS.put(ATRASADO, EnumSet.of(APROVADO, CONCLUIDO));
        ORIGENS.put(
                REEMBOLSADO,
                EnumSet.of(
                        CRIADO, AGUARDANDO_PAGAMENTO, APROVADO, CONCLUIDO, EM_DISPUTA, ATRASADO, CANCELADO, EXPIRADO));
        ORIGENS.put(
                CHARGEBACK,
                EnumSet.of(
                        CRIADO,
                        AGUARDANDO_PAGAMENTO,
                        APROVADO,
                        CONCLUIDO,
                        EM_DISPUTA,
                        ATRASADO,
                        REEMBOLSADO,
                        CANCELADO,
                        EXPIRADO));
        ORIGENS.put(CANCELADO, EnumSet.of(CRIADO, AGUARDANDO_PAGAMENTO, APROVADO, CONCLUIDO, EM_DISPUTA, ATRASADO));
        ORIGENS.put(EXPIRADO, EnumSet.of(CRIADO, AGUARDANDO_PAGAMENTO));
    }

    private final boolean disputaSuspende;

    /**
     * @param disputaSuspende pedido em disputa tira o acesso até a disputa terminar (padrão: mantém)
     */
    public TransicoesPedido(boolean disputaSuspende) {
        this.disputaSuspende = disputaSuspende;
    }

    /**
     * Decide se o evento muda o pedido.
     *
     * @param ultimoEventoEm quando ocorreu o evento da última mudança (nulo se nenhuma)
     */
    public Decisao decidir(StatusPedido atual, StatusPedido novo, Instant ultimoEventoEm, Instant ocorridoEm) {
        if (atual == novo) {
            return Decisao.ignorar("O pedido já está " + atual + ".");
        }
        if (!ORIGENS.get(novo).contains(atual)) {
            return Decisao.ignorar("Mudança de " + atual + " para " + novo + " não é permitida.");
        }
        if (!SEMPRE_VALEM.contains(novo)
                && ultimoEventoEm != null
                && ocorridoEm != null
                && ocorridoEm.isBefore(ultimoEventoEm)) {
            return Decisao.ignorar("Evento anterior à última mudança do pedido.");
        }
        return Decisao.aplicar(efeito(novo));
    }

    /** O que a mudança para o status faz com a matrícula. */
    public EfeitoMatricula efeito(StatusPedido novo) {
        return switch (novo) {
            // CONCLUIDO também concede: a aprovação pode ter se perdido (conceder é idempotente)
            case APROVADO, CONCLUIDO -> EfeitoMatricula.CONCEDER;
            case REEMBOLSADO, CHARGEBACK, CANCELADO -> EfeitoMatricula.CANCELAR;
            case EM_DISPUTA -> disputaSuspende ? EfeitoMatricula.CANCELAR : EfeitoMatricula.NENHUM;
            case CRIADO, AGUARDANDO_PAGAMENTO, ATRASADO, EXPIRADO -> EfeitoMatricula.NENHUM;
        };
    }

    /** A matrícula ligada ao pedido: abrir (ou reabrir/estender), cancelar ou não mexer. */
    public enum EfeitoMatricula {
        CONCEDER,
        CANCELAR,
        NENHUM
    }

    /**
     * @param motivo por que o evento foi ignorado (nulo quando aplica)
     */
    public record Decisao(boolean aplica, EfeitoMatricula efeito, String motivo) {

        static Decisao aplicar(EfeitoMatricula efeito) {
            return new Decisao(true, efeito, null);
        }

        static Decisao ignorar(String motivo) {
            return new Decisao(false, EfeitoMatricula.NENHUM, motivo);
        }
    }
}
