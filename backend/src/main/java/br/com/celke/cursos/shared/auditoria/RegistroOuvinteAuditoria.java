package br.com.celke.cursos.shared.auditoria;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.springframework.stereotype.Component;

/** Liga o {@link OuvinteAuditoria} aos eventos do Hibernate (API e worker). */
@Component
public class RegistroOuvinteAuditoria {

    public RegistroOuvinteAuditoria(EntityManagerFactory emf, OuvinteAuditoria ouvinte) {
        EventListenerRegistry registro =
                emf.unwrap(SessionFactoryImplementor.class).getEventListenerRegistry();
        registro.appendListeners(EventType.POST_INSERT, ouvinte);
        registro.appendListeners(EventType.POST_UPDATE, ouvinte);
        registro.appendListeners(EventType.POST_DELETE, ouvinte);
    }
}
