package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaAssessmentScoreRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spec: evaluation-integrity-guards RF16/RF18 — the exists queries backing the ConflictException
 * guards in SubjectService and CriterionService.
 *
 * <p>Both queries have to see a score through either target, so every case is covered twice: once
 * for a score on an activity item and once for a criterion scored directly. Implicit path
 * navigation would compile to an inner join and silently miss the direct rows.
 */
class AssessmentScoreExistsQueriesIT extends AbstractIntegrationTest {

    @Autowired private JpaAssessmentScoreRepository assessmentScoreRepo;

    private UUID teacher;
    private UUID course;
    private UUID classGroup;
    private UUID subjectId;
    private UUID enrollment;
    private UUID activityCriterion;
    private UUID criterionWithoutScore;

    @BeforeEach
    void seed() {
        teacher = seedUser("Teacher", false);
        course = seedCourse(teacher, "A");
        classGroup = seedClassGroup(course, teacher, "Matematicas");
        subjectId =
                jdbc.queryForObject(
                        "SELECT id_subject FROM class_groups WHERE id_class_group = ?",
                        UUID.class,
                        classGroup);

        activityCriterion =
                seedActivityCriterion(classGroup, 1, "Knowing", "Con notas", "Prueba escrita");
        criterionWithoutScore = seedCriterion(classGroup, 1, "Knowing", "Sin notas");

        enrollment = seedEnrollment(seedStudent(), course);
        seedEventScore(enrollment, seedEvent(activityCriterion, "Prueba 1"), 8.5);
    }

    @Test
    void existsByCriterion_true_whenScoredThroughAnActivityItem() {
        assertThat(assessmentScoreRepo.existsByCriterion(activityCriterion)).isTrue();
    }

    @Test
    void existsByCriterion_true_whenScoredDirectly() {
        UUID direct = seedCriterion(classGroup, 1, "Doing", "Participacion");
        seedCriterionScore(enrollment, direct, 30);

        assertThat(assessmentScoreRepo.existsByCriterion(direct)).isTrue();
    }

    @Test
    void existsByCriterion_false_whenCriterionHasNoScores() {
        assertThat(assessmentScoreRepo.existsByCriterion(criterionWithoutScore)).isFalse();
    }

    @Test
    void existsBySubject_true_whenSubjectHasScores() {
        assertThat(assessmentScoreRepo.existsBySubject(subjectId)).isTrue();
    }

    @Test
    void existsBySubject_true_whenTheOnlyScoreIsDirect() {
        UUID otherClassGroup = seedClassGroup(course, teacher, "Lenguaje");
        UUID otherSubjectId =
                jdbc.queryForObject(
                        "SELECT id_subject FROM class_groups WHERE id_class_group = ?",
                        UUID.class,
                        otherClassGroup);
        UUID direct = seedCriterion(otherClassGroup, 1, "Doing", "Participacion");
        seedCriterionScore(enrollment, direct, 30);

        assertThat(assessmentScoreRepo.existsBySubject(otherSubjectId)).isTrue();
    }

    @Test
    void existsBySubject_false_whenSubjectHasNoScores() {
        UUID otherClassGroup = seedClassGroup(course, teacher, "Lenguaje");
        UUID otherSubjectId =
                jdbc.queryForObject(
                        "SELECT id_subject FROM class_groups WHERE id_class_group = ?",
                        UUID.class,
                        otherClassGroup);

        assertThat(assessmentScoreRepo.existsBySubject(otherSubjectId)).isFalse();
    }
}
