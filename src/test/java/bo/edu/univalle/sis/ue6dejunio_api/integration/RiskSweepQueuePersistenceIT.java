package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.SweepTarget;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskSweepQueueDomain;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The sweep queue against a real Postgres, because everything interesting about it is SQL.
 *
 * <p>A mocked port proves none of it. The coalescing is a primary key; the trimester of a roll call
 * is a join to {@code academic_trimesters}; the fan-out of a daily roll call is a join to {@code
 * class_groups}; and the clear is bounded by a timestamp the database wrote. Each of those can only
 * be wrong here.
 *
 * <p>Trimester periods come seeded by {@code schema-it.sql} for 2026: T1 Feb–May, T2 Jun–Aug, T3
 * Sep–Nov. The dates below are chosen against those and not against today.
 */
class RiskSweepQueuePersistenceIT extends AbstractIntegrationTest {

    /** Inside the seeded second trimester (2026-06-01 .. 2026-08-31). */
    private static final LocalDate IN_TRIMESTER_2 = LocalDate.of(2026, 6, 15);

    /** December: after the last configured period ends, so it belongs to no trimester at all. */
    private static final LocalDate OUTSIDE_EVERY_TRIMESTER = LocalDate.of(2026, 12, 20);

    @Autowired private IRiskSweepQueueDomain queue;

    private UUID courseId;
    private UUID mathGroup;
    private UUID languageGroup;

    @BeforeEach
    void seedClassroom() {
        UUID teacher = seedUser("Teacher", false);
        courseId = seedCourse(teacher, "A");
        mathGroup = seedClassGroup(courseId, teacher, "Matematicas");
        languageGroup = seedClassGroup(courseId, teacher, "Lenguaje");
    }

    private LocalDateTime markedAtOf(UUID classGroupId, int trimester) {
        return jdbc.queryForObject(
                "SELECT marked_at FROM risk_prediction_queue "
                        + "WHERE id_class_group = ? AND trimester = ?",
                LocalDateTime.class,
                classGroupId,
                trimester);
    }

    private void backdate(UUID classGroupId, int trimester, LocalDateTime when) {
        jdbc.update(
                "UPDATE risk_prediction_queue SET marked_at = ? "
                        + "WHERE id_class_group = ? AND trimester = ?",
                when,
                classGroupId,
                trimester);
    }

    @Test
    void markClassGroups_leavesOneStandingRequest() {
        queue.markClassGroups(List.of(mathGroup), 1);

        assertThat(queue.pending(10))
                .extracting(SweepTarget::classGroupId, SweepTarget::trimester)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(mathGroup, 1));
    }

    /**
     * The whole point of the table. Thirty grades entered one after another in the same subject
     * have to become one prediction run, and the primary key is what makes that true.
     */
    @Test
    void markClassGroups_twice_isStillOneRow() {
        queue.markClassGroups(List.of(mathGroup), 1);
        queue.markClassGroups(List.of(mathGroup), 1);
        queue.markClassGroups(List.of(mathGroup), 1);

        assertThat(queue.pending(10)).hasSize(1);
    }

    /**
     * The conflict adds no row but must still move the instant.
     *
     * <p>Frozen at the first save, a change arriving while a sweep is asking the model would be
     * deleted by a clear bounded on a later instant, having never been predicted. This is the
     * behaviour that makes {@code clearSwept_leavesAMarkThatLandedAfterTheSweepRead} reachable in
     * production rather than only when a test writes the timestamp by hand.
     */
    @Test
    void markClassGroups_again_movesTheInstantForwardWithoutAddingARow() {
        queue.markClassGroups(List.of(mathGroup), 1);
        LocalDateTime firstMark = LocalDateTime.of(2026, 6, 1, 8, 0);
        backdate(mathGroup, 1, firstMark);

        queue.markClassGroups(List.of(mathGroup), 1);

        assertThat(queue.pending(10)).hasSize(1);
        assertThat(markedAtOf(mathGroup, 1)).isAfter(firstMark);
    }

    /**
     * The race the refresh exists for, played out through the port alone.
     *
     * <p>A sweep reads the queue, and while the model is answering the teacher saves again. The
     * clear is bounded by what the sweep read; the second save moved the row past it, so the row
     * survives and the next tick predicts the change instead of losing it.
     */
    @Test
    void aSaveDuringASweep_survivesTheClearThatFollows() {
        queue.markClassGroups(List.of(mathGroup), 1);
        List<SweepTarget> read = queue.pending(10);
        LocalDateTime sweepReadAt = read.get(0).markedAt();

        // The teacher saving again while the model is still answering.
        queue.markClassGroups(List.of(mathGroup), 1);
        queue.clearSwept(List.of(mathGroup), 1, sweepReadAt);

        assertThat(queue.pending(10)).hasSize(1);
    }

    /**
     * A subject nobody teaches any more cannot be predicted, and must not break the write either.
     */
    @Test
    void markClassGroups_inactiveSubject_marksNothing() {
        jdbc.update(
                "UPDATE class_groups SET is_active = false WHERE id_class_group = ?", mathGroup);

        queue.markClassGroups(List.of(mathGroup), 1);

        assertThat(queue.pending(10)).isEmpty();
    }

    /** A roll call is dated; which trimester that is belongs to the course's own gestión. */
    @Test
    void markClassGroupsOn_resolvesTheTrimesterFromTheDate() {
        queue.markClassGroupsOn(List.of(mathGroup), IN_TRIMESTER_2);

        assertThat(queue.pending(10))
                .extracting(SweepTarget::classGroupId, SweepTarget::trimester)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(mathGroup, 2));
    }

    /**
     * A date in nobody's trimester marks nothing, and that is the right answer rather than a
     * failure: there is no trimester for the model to be asked about.
     */
    @Test
    void markClassGroupsOn_dateOutsideEveryPeriod_marksNothing() {
        queue.markClassGroupsOn(List.of(mathGroup), OUTSIDE_EVERY_TRIMESTER);

        assertThat(queue.pending(10)).isEmpty();
    }

    /**
     * The daily roll call carries a null {@code id_class_group}, so it is the attendance every
     * subject of the course falls back to. One roll call therefore moves all of them.
     */
    @Test
    void markCoursesOfEnrollmentsOn_marksEverySubjectOfTheCourse() {
        UUID enrollment = seedEnrollment(seedStudent("Ana", "Alvarez"), courseId);

        queue.markCoursesOfEnrollmentsOn(List.of(enrollment), IN_TRIMESTER_2);

        assertThat(queue.pending(10))
                .extracting(SweepTarget::classGroupId, SweepTarget::trimester)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(mathGroup, 2),
                        org.assertj.core.groups.Tuple.tuple(languageGroup, 2));
    }

    /**
     * The real shape of a roll call, and the one a single-student test cannot reach.
     *
     * <p>Every enrolment of the course emits the same {@code (class group, trimester)} tuple, and
     * Postgres refuses duplicate arbiter tuples inside one {@code ON CONFLICT DO UPDATE}: {@code
     * ERROR: ON CONFLICT DO UPDATE command cannot affect row a second time}. With thirty students
     * that is every whole-classroom roll call in the school, and the listener swallows the failure
     * — attendance would commit and queue nothing, forever, in silence.
     */
    @Test
    void markCoursesOfEnrollmentsOn_wholeClassroom_marksEachSubjectOnce() {
        List<UUID> roster =
                List.of(
                        seedEnrollment(seedStudent("Ana", "Alvarez"), courseId),
                        seedEnrollment(seedStudent("Bruno", "Bermudez"), courseId),
                        seedEnrollment(seedStudent("Carla", "Chavez"), courseId));

        queue.markCoursesOfEnrollmentsOn(roster, IN_TRIMESTER_2);

        assertThat(queue.pending(10))
                .extracting(SweepTarget::classGroupId, SweepTarget::trimester)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(mathGroup, 2),
                        org.assertj.core.groups.Tuple.tuple(languageGroup, 2));
    }

    @Test
    void markCoursesOfEnrollmentsOn_skipsTheSubjectsNobodyTeaches() {
        jdbc.update(
                "UPDATE class_groups SET is_active = false WHERE id_class_group = ?",
                languageGroup);
        UUID enrollment = seedEnrollment(seedStudent("Ana", "Alvarez"), courseId);

        queue.markCoursesOfEnrollmentsOn(List.of(enrollment), IN_TRIMESTER_2);

        assertThat(queue.pending(10))
                .extracting(SweepTarget::classGroupId)
                .containsExactly(mathGroup);
    }

    @Test
    void pending_answersOldestFirstAndNoMoreThanAsked() {
        queue.markClassGroups(List.of(mathGroup), 1);
        queue.markClassGroups(List.of(languageGroup), 1);
        backdate(languageGroup, 1, LocalDateTime.of(2026, 6, 1, 7, 0));
        backdate(mathGroup, 1, LocalDateTime.of(2026, 6, 1, 8, 0));

        assertThat(queue.pending(10))
                .extracting(SweepTarget::classGroupId)
                .containsExactly(languageGroup, mathGroup);
        assertThat(queue.pending(1))
                .extracting(SweepTarget::classGroupId)
                .containsExactly(languageGroup);
    }

    @Test
    void clearSwept_removesWhatWasSwept() {
        queue.markClassGroups(List.of(mathGroup, languageGroup), 1);

        int removed = queue.clearSwept(List.of(mathGroup, languageGroup), 1, LocalDateTime.now());

        assertThat(removed).isEqualTo(2);
        assertThat(queue.pending(10)).isEmpty();
    }

    /**
     * The race the {@code marked_at} column exists for. A teacher saving a grade while the model is
     * answering leaves a mark the sweep never read; clearing it would delete that change having
     * never predicted it, and nothing anywhere would say so.
     */
    @Test
    void clearSwept_leavesAMarkThatLandedAfterTheSweepRead() {
        queue.markClassGroups(List.of(mathGroup), 1);
        LocalDateTime sweepReadAt = LocalDateTime.of(2026, 6, 1, 8, 0);
        // The save that landed while the model was answering.
        backdate(mathGroup, 1, sweepReadAt.plusSeconds(1));

        int removed = queue.clearSwept(List.of(mathGroup), 1, sweepReadAt);

        assertThat(removed).isZero();
        assertThat(queue.pending(10)).hasSize(1);
    }

    /** A clear for one trimester must not take the same subject's other trimester with it. */
    @Test
    void clearSwept_doesNotTouchTheSameSubjectsOtherTrimester() {
        queue.markClassGroups(List.of(mathGroup), 1);
        queue.markClassGroups(List.of(mathGroup), 2);

        queue.clearSwept(List.of(mathGroup), 1, LocalDateTime.now());

        assertThat(queue.pending(10)).extracting(SweepTarget::trimester).containsExactly(2);
    }
}
