package bo.edu.univalle.sis.ue6dejunio_api.application.services.notification;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.NotificationType;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.SendNotificationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.PdcStatus;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.PdcStatusChanged;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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

    private final INotificationService notificationService;
    private final INotificationDomain notificationDomain;

    public PdcNotificationListener(INotificationService notificationService,
                                   INotificationDomain notificationDomain) {
        this.notificationService = notificationService;
        this.notificationDomain = notificationDomain;
    }

    /**
     * Runs once the change is really in the database, and in a transaction of its own.
     *
     * <p>{@code AFTER_COMMIT} is the whole reason this is an event rather than a call inside the
     * service: a publish that was refused rolls back, and a teacher must not read that a plan was
     * handed in when nothing was. That phase runs outside the original transaction, though, which
     * is why the write needs {@code REQUIRES_NEW} — without it there is no transaction to write
     * in and the notification is silently lost.
     *
     * <p>Failures are swallowed deliberately. The plan is already committed and correct; throwing
     * here cannot undo it and would only turn a delivered state change into an error the caller
     * cannot act on. What is lost is a message, and that is worth a log, not a 500.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPdcStatusChanged(PdcStatusChanged event) {
        try {
            if (PdcStatus.PUBLISHED.equals(event.status())) {
                tellEveryDirector(event);
            } else if (PdcStatus.APPROVED.equals(event.status())) {
                tellAuthor(event, NotificationType.PDC_APPROVED,
                    "Tu %s fue aprobado.".formatted(planLabel(event)));
            } else if (PdcStatus.WITH_OBSERVATIONS.equals(event.status())) {
                tellAuthor(event, NotificationType.PDC_OBSERVED,
                    "Tu %s fue observado: %s".formatted(planLabel(event), event.observations()));
            }
            // Draft and Under Review are steps nobody is waiting on, and a status this listener
            // does not know is one nobody asked it to announce.
        } catch (RuntimeException ex) {
            log.error("The plan {} changed to {} and the notification could not be written",
                event.planId(), event.status(), ex);
        }
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
                "Un %s fue entregado para revision.".formatted(planLabel(event)));
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

    private void send(UUID receiver, PdcStatusChanged event, NotificationType type,
                      String message) {
        // No sender: the system wrote this, and putting a name on it would credit a person for a
        // line nobody typed.
        notificationService.send(new SendNotificationCommand(
            null, receiver, type, null, message, PLAN_RESOURCE, event.planId()));
    }

    private static String planLabel(PdcStatusChanged event) {
        return "PDC N° %d del trimestre %d".formatted(event.planNumber(), event.trimester());
    }
}
