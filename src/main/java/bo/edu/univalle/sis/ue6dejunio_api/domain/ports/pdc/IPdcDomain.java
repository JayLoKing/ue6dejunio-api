package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.pdc;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.Pdc;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.UpsertPdcSubjectCommand;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface IPdcDomain {

    Pdc save(Pdc pdc);

    Optional<Pdc> findById(UUID id);

    /**
     * The plan's status alone, for the guards that only ask whether it can still be written. Empty
     * when there is no such plan.
     */
    Optional<String> statusOf(UUID planId);

    boolean courseExists(UUID courseId);

    /** One plan per course per numbered month: the second August plan of a course is a mistake. */
    boolean existsByCoursePlanNumber(UUID courseId, Integer trimester, Integer planNumber);

    /** The active class groups of a course that this teacher runs, in the order they print. */
    List<UUID> classGroupIdsTaughtBy(UUID courseId, UUID teacherId);

    /** Opens the subject blocks of a plan, one per class group, keeping the given order. */
    Pdc addSubjects(UUID planId, List<UUID> classGroupIds);

    /**
     * Replaces a subject block's objective, adaptations and weekly rows, and stamps who did it. The
     * author travels with the write so the plan is not read and saved a second time just to record
     * it.
     */
    Pdc writeSubject(
            UUID planId, UUID planSubjectId, UpsertPdcSubjectCommand command, UUID currentUserId);

    /** The courses that share a plan's grade and year — the parallels the rotation copies into. */
    List<UUID> siblingCourseIdsOf(UUID courseId);

    /**
     * Which of those courses already hold that numbered month, asked in one query. The rotation
     * skips them, and asking course by course was a round trip per parallel.
     */
    Set<UUID> courseIdsWithPlan(List<UUID> courseIds, Integer trimester, Integer planNumber);

    /**
     * Copies a plan into the given courses, blocks and rows included, one fresh draft each.
     *
     * <p>Takes the whole list rather than one course at a time so the source is read once: a JPQL
     * query runs against the database every time it is issued, first-level cache or not.
     */
    List<Pdc> copyTo(UUID sourcePlanId, List<UUID> targetCourseIds, UUID currentUserId);

    /** Whether any plan was copied from this one. Deleting an original would orphan them. */
    boolean hasCopies(UUID planId);

    /**
     * The teachers who may write this plan: the homeroom teacher of its course plus the teacher of
     * each subject block.
     *
     * <p>The ownership guard runs before every write and only needs to compare ids, so it asks for
     * ids. Reading the plan through {@link #findById} instead pulled every block and every weekly
     * row to answer a question about a handful of UUIDs.
     */
    Set<UUID> writerIdsOf(UUID planId);

    /** The teachers who may write one block: the homeroom teacher, or that block's own teacher. */
    Set<UUID> subjectWriterIdsOf(UUID planId, UUID planSubjectId);

    /**
     * Who may act on the plan as a whole — publish it, delete it, hand it to the parallels.
     *
     * <p>Narrower than {@link #writerIdsOf}: the teacher who opened the plan and the homeroom
     * teacher of its course, not every teacher holding a block. A specialist writes their own
     * subject, but publishing a homeroom teacher's course-wide plan for review is not theirs to do.
     */
    Set<UUID> administratorIdsOf(UUID planId);

    /**
     * @param courseId narrows to one course; {@code null} spans every course.
     * @param teacherId narrows the listing to the plans a teacher takes part in; {@code null} spans
     *     every plan, which only the Director and the secretariat are entitled to.
     */
    PageResult<Pdc> list(
            UUID courseId,
            Integer trimester,
            String status,
            String excludeStatus,
            UUID teacherId,
            PageQuery pageQuery);

    void deleteById(UUID id);
}
