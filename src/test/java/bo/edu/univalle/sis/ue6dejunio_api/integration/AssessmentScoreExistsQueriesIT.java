package bo.edu.univalle.sis.ue6dejunio_api.integration;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaAssessmentScoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec: evaluation-integrity-guards RF16/RF18 — derived existsBy queries backing the
 * ConflictException guards in SubjectService and CriterionService.
 */
class AssessmentScoreExistsQueriesIT extends AbstractIntegrationTest {

    @Autowired private JpaAssessmentScoreRepository assessmentScoreRepo;

    private UUID teacher;
    private UUID course;
    private UUID classGroup;
    private UUID subjectId;
    private UUID criterionWithScore;
    private UUID criterionWithoutScore;

    @BeforeEach
    void seed() {
        teacher = seedUser("Teacher", false);
        course = seedCourse(teacher, "A");
        classGroup = seedClassGroup(course, teacher, "Matematicas");
        subjectId = jdbc.queryForObject(
            "SELECT id_subject FROM class_groups WHERE id_class_group = ?", UUID.class, classGroup);

        criterionWithScore = seedCriterion(classGroup, 1, "Knowing", "Con notas");
        criterionWithoutScore = seedCriterion(classGroup, 1, "Knowing", "Sin notas");

        UUID event = seedEvent(criterionWithScore, "Prueba 1", 10.0);
        UUID student = seedStudent();
        UUID enrollment = seedEnrollment(student, course);
        jdbc.update(
            "INSERT INTO assessment_scores (id_course_enrollment, id_assessment_event, score) "
                + "VALUES (?,?,?)",
            enrollment, event, 8.5);
    }

    @Test
    void existsByEventCriterionId_true_whenCriterionHasScores() {
        assertThat(assessmentScoreRepo.existsByEvent_Criterion_Id(criterionWithScore)).isTrue();
    }

    @Test
    void existsByEventCriterionId_false_whenCriterionHasNoScores() {
        assertThat(assessmentScoreRepo.existsByEvent_Criterion_Id(criterionWithoutScore)).isFalse();
    }

    @Test
    void existsByEventCriterionClassGroupSubjectId_true_whenSubjectHasScores() {
        assertThat(assessmentScoreRepo.existsByEvent_Criterion_ClassGroup_Subject_Id(subjectId)).isTrue();
    }

    @Test
    void existsByEventCriterionClassGroupSubjectId_false_whenSubjectHasNoScores() {
        UUID otherClassGroup = seedClassGroup(course, teacher, "Lenguaje");
        UUID otherSubjectId = jdbc.queryForObject(
            "SELECT id_subject FROM class_groups WHERE id_class_group = ?", UUID.class, otherClassGroup);
        assertThat(assessmentScoreRepo.existsByEvent_Criterion_ClassGroup_Subject_Id(otherSubjectId)).isFalse();
    }
}
