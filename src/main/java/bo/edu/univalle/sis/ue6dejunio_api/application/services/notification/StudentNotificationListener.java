package bo.edu.univalle.sis.ue6dejunio_api.application.services.notification;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.NotificationType;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.SendNotificationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentWithdrawn;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationDomain;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns a student leaving into the teachers who need to hear about it.
 *
 * <p>On the notification side of the wall, for the same reason the PDC listener is: {@code
 * StudentService} states that a student was taken off the roll and stops there. Who keeps a roster
 * with that name on it is a question about the school, not about a student record.
 */
@Component
public class StudentNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(StudentNotificationListener.class);

    /** What the notice points at, so the teacher opens the student rather than hunting for them. */
    private static final String STUDENT_RESOURCE = "STUDENT";

    private final NotificationDispatcher dispatcher;
    private final INotificationDomain notificationDomain;

    public StudentNotificationListener(
            NotificationDispatcher dispatcher, INotificationDomain notificationDomain) {
        this.dispatcher = dispatcher;
        this.notificationDomain = notificationDomain;
    }

    /**
     * Runs once the withdrawal is really in the database.
     *
     * <p>{@code AFTER_COMMIT} is the whole reason this is an event: a withdrawal that is refused
     * rolls back, and a teacher must not read that a student left when the student is still there.
     *
     * <p>Carries no transaction of its own. Each send opens one through the dispatcher, so a
     * failure is one lost message rather than all of them.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStudentWithdrawn(StudentWithdrawn event) {
        List<UUID> teachers = notificationDomain.teacherIdsResponsibleForStudent(event.studentId());
        if (teachers.isEmpty()) {
            // A student nobody was teaching. Worth knowing, not worth an error.
            log.info(
                    "The student {} was withdrawn and no active teacher answered for them",
                    event.studentId());
            return;
        }
        String message = messageFor(event);
        for (UUID teacher : teachers) {
            send(teacher, event, message);
        }
    }

    /**
     * What the teacher reads: who left, under which category, and — when the Director wrote any —
     * in their words. Everything the notice is for, without opening anything.
     */
    private static String messageFor(StudentWithdrawn event) {
        String base =
                "%s fue dado de baja de tu curso. Motivo: %s."
                        .formatted(event.studentName(), event.reason());
        return event.note() == null ? base : base + " " + event.note();
    }

    /**
     * One recipient, one outcome.
     *
     * <p>Caught here rather than higher up so telling four teachers is four attempts. The student
     * is already withdrawn and correct; a message that could not be written cannot undo that, and
     * it must not cost the other teachers theirs.
     */
    private void send(UUID teacher, StudentWithdrawn event, String message) {
        try {
            // No sender: the withdrawal wrote this, and putting a name on it would credit a person
            // for a line nobody typed.
            dispatcher.deliver(
                    new SendNotificationCommand(
                            null,
                            teacher,
                            NotificationType.STUDENT_WITHDRAWN,
                            null,
                            message,
                            STUDENT_RESOURCE,
                            event.studentId()));
        } catch (RuntimeException ex) {
            log.error(
                    "The student {} was withdrawn and {} could not be told",
                    event.studentId(),
                    teacher,
                    ex);
        }
    }
}
