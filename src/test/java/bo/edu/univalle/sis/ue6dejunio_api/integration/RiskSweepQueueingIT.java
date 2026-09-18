package bo.edu.univalle.sis.ue6dejunio_api.integration;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentDimension;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.SetScoreCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.CreateCriterionCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.SweepTarget;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentScoreService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.criterion.ICriterionService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskSweepQueueDomain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF 30, end to end and without a scheduler: a teacher saves, and the queue has a row.
 *
 * <p>This is the one thing the unit tests cannot prove. They show that the services publish and
 * that the listener marks — but between those two sits {@code AFTER_COMMIT}, and whether an event
 * published inside a service's transaction ever reaches a listener is a fact about Spring and a
 * real transaction manager, not about either class. Mocked, it passes whether the wiring exists or
 * not.
 *
 * <p>Attendance is deliberately not driven here. Its writes refuse any date outside the current ISO
 * week, so an end-to-end attendance test would have to use {@code LocalDate.now()} — and would then
 * start failing on the first weekend, or the first day outside a configured trimester. What it
 * would add is covered where it can be stated without a calendar: the publishing in
 * {@code AttendanceServiceTest}, and the date-to-trimester join in
 * {@code RiskSweepQueuePersistenceIT}.
 */
class RiskSweepQueueingIT extends AbstractIntegrationTest {

    @Autowired
    private IAssessmentScoreService scores;

    @Autowired
    private ICriterionService criteria;

    @Autowired
    private IRiskSweepQueueDomain queue;

    private UUID classGroupId;
    private UUID enrollmentId;

    @BeforeEach
    void seedClassroom() {
        UUID teacher = seedUser("Teacher", false);
        UUID courseId = seedCourse(teacher, "A");
        classGroupId = seedClassGroup(courseId, teacher, "Matematicas");
        enrollmentId = seedEnrollment(seedStudent("Ana", "Alvarez"), courseId);
    }

    @Test
    void savingAMark_queuesItsSubjectForTheNextSweep() {
        UUID criterionId = seedCriterion(classGroupId, 1, AssessmentDimension.KNOWING, "Prueba");
        // Seeding the criterion writes rows directly, so the queue is empty going in: what the
        // assertion below finds can only have come from the score.
        assertThat(queue.pending(10)).isEmpty();

        scores.setScore(new SetScoreCommand(
            enrollmentId, null, criterionId, new BigDecimal("40.00"), null));

        assertThat(queue.pending(10))
            .extracting(SweepTarget::classGroupId, SweepTarget::trimester)
            .containsExactly(org.assertj.core.groups.Tuple.tuple(classGroupId, 1));
    }

    /** Correcting a mark is a change the model has to see, exactly like entering one. */
    @Test
    void correctingAMark_queuesTheSubjectAgainAfterItWasSwept() {
        UUID criterionId = seedCriterion(classGroupId, 1, AssessmentDimension.KNOWING, "Prueba");
        scores.setScore(new SetScoreCommand(
            enrollmentId, null, criterionId, new BigDecimal("40.00"), null));
        queue.clearSwept(java.util.List.of(classGroupId), 1, java.time.LocalDateTime.now());
        assertThat(queue.pending(10)).isEmpty();

        scores.setScore(new SetScoreCommand(
            enrollmentId, null, criterionId, new BigDecimal("20.00"), null));

        assertThat(queue.pending(10)).hasSize(1);
    }

    /**
     * Planning a criterion moves the model's progress denominator without a single mark changing:
     * three marks out of three and three out of seven are the same count and mean opposite things.
     */
    @Test
    void planningACriterion_queuesTheSubjectWithNoMarkInvolved() {
        criteria.create(new CreateCriterionCommand(
            classGroupId, 2, AssessmentDimension.DOING, "Trabajo practico", null, null, null));

        assertThat(queue.pending(10))
            .extracting(SweepTarget::classGroupId, SweepTarget::trimester)
            .containsExactly(org.assertj.core.groups.Tuple.tuple(classGroupId, 2));
    }
}
