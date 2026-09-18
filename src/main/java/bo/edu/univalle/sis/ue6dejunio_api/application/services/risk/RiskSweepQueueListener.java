package bo.edu.univalle.sis.ue6dejunio_api.application.services.risk;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.DailyAttendanceRecorded;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskInputsChanged;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.SessionAttendanceRecorded;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskSweepQueueDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Turns "a teacher changed something the model reads" into a row in the sweep queue.
 *
 * <p>This is the whole of RF 30's "automatically". The services that write marks and attendance
 * state what they did and stop; that those rows are also the model's inputs is not their concern,
 * and a gradebook that had to remember to schedule a prediction would forget on the next path
 * somebody added.
 *
 * <p><b>{@code AFTER_COMMIT} is not a detail.</b> Two reasons, and either alone would be enough.
 * A write that rolls back must not queue a prediction over data that never landed — the sweep would
 * run on the previous marks and file an answer nobody's action produced. And the mark must not be
 * able to break the save it observes: inside the transaction, a failed insert here poisons it, and
 * a teacher would be told their grade was rejected because a queue row could not be written.
 */
@Component
public class RiskSweepQueueListener {

    private static final Logger log = LoggerFactory.getLogger(RiskSweepQueueListener.class);

    private final IRiskSweepQueueDomain queue;

    public RiskSweepQueueListener(IRiskSweepQueueDomain queue) {
        this.queue = queue;
    }

    /** A mark was entered, corrected or removed, or the planned criteria of a subject changed. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRiskInputsChanged(RiskInputsChanged event) {
        mark(() -> queue.markClassGroups(List.of(event.classGroupId()), event.trimester()),
            "subject " + event.classGroupId() + " trimester " + event.trimester());
    }

    /** A roll call taken inside one subject. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSessionAttendanceRecorded(SessionAttendanceRecorded event) {
        mark(() -> queue.markClassGroupsOn(List.of(event.classGroupId()), event.date()),
            "subject " + event.classGroupId() + " on " + event.date());
    }

    /** The classroom-wide roll call, which moves the attendance of every subject of that course. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDailyAttendanceRecorded(DailyAttendanceRecorded event) {
        mark(() -> queue.markCoursesOfEnrollmentsOn(event.courseEnrollmentIds(), event.date()),
            event.courseEnrollmentIds().size() + " enrolment(s) on " + event.date());
    }

    /**
     * One marking, and what it costs to lose.
     *
     * <p>Caught rather than allowed to propagate. The write this stands for is already committed
     * and correct; a queue row that could not be written must not surface as a failure of a save
     * that succeeded. What it does cost is real and is why this logs at error: that subject will
     * not be re-predicted until something else touches it.
     */
    private void mark(Runnable marking, String what) {
        try {
            marking.run();
        } catch (RuntimeException ex) {
            log.error("The risk sweep queue could not be marked for {}; the change is saved but the "
                + "model will not see it until that subject is touched again", what, ex);
        }
    }
}
