package bo.edu.univalle.sis.ue6dejunio_api.application.services.notification;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.NotificationType;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.SendNotificationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.PdcStatus;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.PdcStatusChanged;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

/**
 * Turns what happened to a plan into who needs to hear about it.
 *
 * <p>This lives on the notification side of the wall on purpose. {@code PdcService} states that a
 * plan was handed in and stops there — it does not know that notifications exist, cannot be broken
 * by them, and needs no notification double in its tests. Routing is a question about the school,
 * not about planning, and it is answered here.
 */
@Component
public class PdcNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(PdcNotificationListener.class);

    /** What the notification points at, so the receiver opens the plan rather than hunting it. */
    private static final String PLAN_RESOURCE = "CURRICULUM_PLAN";

    private final NotificationDispatcher dispatcher;
    private final INotificationDomain notificationDomain;

    public PdcNotificationListener(NotificationDispatcher dispatcher,
                                   INotificationDomain notificationDomain) {
        this.dispatcher = dispatcher;
        this.notificationDomain = notificationDomain;
    }

    /**
     * Runs once the change is really in the database.
     *
     * <p>{@code AFTER_COMMIT} is the whole reason this is an event rather than a call inside the
     * service: a publish that was refused rolls back, and a teacher must not read that a plan was
     * handed in when nothing was.
     *
     * <p>Carries no transaction of its own on purpose. Each send opens one through the dispatcher,
     * so a failure is one lost message rather than all of them — and the catch below sits outside
     * any boundary, which is what lets it actually swallow rather than fail again on commit.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPdcStatusChanged(PdcStatusChanged event) {
        if (PdcStatus.PUBLISHED.equals(event.status())) {
            tellEveryDirector(event);
        } else if (PdcStatus.APPROVED.equals(event.status())) {
            tellAuthor(event, NotificationType.PDC_APPROVED,
                "Tu %s fue aprobado.".formatted(planLabel(event)));
        } else if (PdcStatus.WITH_OBSERVATIONS.equals(event.status())) {
            tellAuthor(event, NotificationType.PDC_OBSERVED,
                "Tu %s fue observado: %s".formatted(planLabel(event), event.observations()));
        }
        // Draft and Under Review are steps nobody is waiting on, and a status this listener does
        // not know is one nobody asked it to announce.
    }

    private void tellEveryDirector(PdcStatusChanged event) {
        List<UUID> directors = notificationDomain.activeDirectorIds();
        if (directors.isEmpty()) {
            log.warn("The plan {} was handed in and the school has no active Director to review it",
                event.planId());
            return;
        }
        for (UUID director : directors) {
            send(director, event, NotificationType.PDC_PUBLISHED,
                "Un %s fue entregado para revisión.".formatted(planLabel(event)));
        }
    }

    private void tellAuthor(PdcStatusChanged event, NotificationType type, String message) {
        if (event.authorId() == null) {
            log.warn("The plan {} changed to {} and has no author to tell",
                event.planId(), event.status());
            return;
        }
        send(event.authorId(), event, type, message);
    }

    /**
     * One recipient, one outcome.
     *
     * <p>Caught here rather than higher up so telling three people is three attempts. The plan is
     * already committed and correct; a message that could not be written cannot undo it, and
     * turning that into an error the caller can do nothing about helps nobody. It is worth a log.
     */
    private void send(UUID receiver, PdcStatusChanged event, NotificationType type,
                      String message) {
        try {
            // No sender: the system wrote this, and putting a name on it would credit a person for
            // a line nobody typed.
            dispatcher.deliver(new SendNotificationCommand(
                null, receiver, type, null, message, PLAN_RESOURCE, event.planId()));
        } catch (RuntimeException ex) {
            log.error("The plan {} changed to {} and {} could not be told",
                event.planId(), event.status(), receiver, ex);
        }
    }

    private static String planLabel(PdcStatusChanged event) {
        return "PDC N° %d del trimestre %d".formatted(event.planNumber(), event.trimester());
    }
}
