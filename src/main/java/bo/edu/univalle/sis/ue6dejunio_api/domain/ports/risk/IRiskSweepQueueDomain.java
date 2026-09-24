package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.SweepTarget;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The queue of subjects whose model inputs changed and have not been re-predicted yet.
 *
 * <p>Every marking method is one statement. That is the point: these run inside the transaction of
 * a teacher saving a mark or a roll call, and a queue that cost three round trips per save would be
 * a worse tax than the prediction it exists to defer. The resolving a mark needs — which subjects
 * belong to a course, which trimester a date falls in — is done by the database in the same
 * statement that inserts, because it is a join over rows and not a rule about the model.
 *
 * <p><b>Marking adds no second row but does move the instant on the one already there.</b> The
 * primary key is the coalescing; {@code markedAt} is what the sweep clears against, so it has to
 * name the most recent change. Frozen at the first save, a change arriving mid-sweep would be
 * deleted by a clear bounded on a later instant, having never been predicted. Moving it cannot
 * starve a busy classroom either: such a row is still returned by {@link #pending(int)} and still
 * predicted every sweep — only its deletion waits for the edits to stop.
 */
public interface IRiskSweepQueueDomain {

    /**
     * Marks these subjects for a trimester the caller already knows.
     *
     * <p>The path marks and planned criteria take: both are stored against a criterion that names
     * its own trimester, so nothing has to be derived.
     */
    void markClassGroups(Collection<UUID> classGroupIds, int trimester);

    /**
     * Marks these subjects for whichever trimester contains {@code date}.
     *
     * <p>Attendance rows are dated and the model is per trimester, so the trimester is resolved
     * from {@code academic_trimesters} against the course's own gestión. A date outside every
     * configured period marks nothing — and that is correct, not a failure: there is no trimester
     * for the model to be asked about.
     */
    void markClassGroupsOn(Collection<UUID> classGroupIds, LocalDate date);

    /**
     * Marks every active subject of the courses these enrolments belong to, for the trimester
     * containing {@code date}.
     *
     * <p>Daily attendance is taken once for the whole classroom — {@code attendance.id_class_group}
     * is null on those rows — so a single roll call changes the attendance feature of every subject
     * in that course, not of one.
     */
    void markCoursesOfEnrollmentsOn(Collection<UUID> courseEnrollmentIds, LocalDate date);

    /**
     * The oldest standing requests, oldest first.
     *
     * <p>Bounded because a sweep that woke up to a school-sized queue would hold the model for as
     * long as it took to drain it. Oldest first so nothing can starve behind a classroom that keeps
     * being edited.
     */
    List<SweepTarget> pending(int limit);

    /**
     * Removes the marks that were swept, and only those.
     *
     * <p>Takes one trimester rather than a set of pairs because that is how a sweep spends the
     * queue: the model is asked per trimester, so a clear follows one answer about one trimester. A
     * signature carrying mixed pairs would have to be matched as a row value, and could clear a
     * subject's <em>other</em> trimester the moment a drain held both.
     *
     * @param asOf the newest mark {@link #pending(int)} returned. A row marked after it belongs to
     *     a change the sweep did not see, so it survives and the next tick takes it.
     * @return how many rows were removed
     */
    int clearSwept(Collection<UUID> classGroupIds, int trimester, LocalDateTime asOf);
}
