package br.com.celke.cursos.shared.auditoria;

import java.util.Map;
import org.hibernate.Hibernate;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.stereotype.Component;

/**
 * Ouve as inclusões, alterações e exclusões que o Hibernate grava e registra cada uma na trilha de
 * auditoria, dentro da mesma transação. Na alteração, só os campos que mudaram, com o antes e o
 * depois. Entidades {@link SemAuditoria} ficam de fora. Mudanças feitas direto em SQL (fila,
 * notificações de pagamento, atualizações em lote) não passam por aqui.
 */
@Component
public class OuvinteAuditoria implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    private final TrilhaAuditoria trilha;

    public OuvinteAuditoria(TrilhaAuditoria trilha) {
        this.trilha = trilha;
    }

    @Override
    public void onPostInsert(PostInsertEvent evento) {
        Class<?> tipo = Hibernate.getClass(evento.getEntity());
        if (auditada(tipo)) {
            registrar(
                    OperacaoAuditoria.INCLUSAO,
                    tipo,
                    evento.getId(),
                    null,
                    ValoresAuditoria.campos(tipo, nomes(evento.getPersister()), evento.getState(), null));
        }
    }

    @Override
    public void onPostUpdate(PostUpdateEvent evento) {
        Class<?> tipo = Hibernate.getClass(evento.getEntity());
        int[] alterados = evento.getDirtyProperties();
        if (!auditada(tipo) || (alterados != null && alterados.length == 0)) {
            return;
        }
        String[] nomes = nomes(evento.getPersister());
        registrar(
                OperacaoAuditoria.ALTERACAO,
                tipo,
                evento.getId(),
                ValoresAuditoria.campos(tipo, nomes, evento.getOldState(), alterados),
                ValoresAuditoria.campos(tipo, nomes, evento.getState(), alterados));
    }

    @Override
    public void onPostDelete(PostDeleteEvent evento) {
        Class<?> tipo = Hibernate.getClass(evento.getEntity());
        if (auditada(tipo)) {
            registrar(
                    OperacaoAuditoria.EXCLUSAO,
                    tipo,
                    evento.getId(),
                    ValoresAuditoria.campos(tipo, nomes(evento.getPersister()), evento.getDeletedState(), null),
                    null);
        }
    }

    /** O registro entra na transação da mudança; nada fica pendente para depois do commit. */
    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    static boolean auditada(Class<?> tipo) {
        return !tipo.isAnnotationPresent(SemAuditoria.class);
    }

    private void registrar(
            OperacaoAuditoria operacao,
            Class<?> tipo,
            Object id,
            Map<String, Object> antes,
            Map<String, Object> depois) {
        trilha.registrar(
                operacao,
                tipo.getSimpleName(),
                id == null ? null : String.valueOf(ValoresAuditoria.simples(id)),
                antes,
                depois,
                ContextoAuditoria.atual());
    }

    private static String[] nomes(EntityPersister persister) {
        return persister.getPropertyNames();
    }
}
