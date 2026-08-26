package bo.edu.univalle.sis.ue6dejunio_api.application.assessment;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.assessment.AssessmentScoreService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentEvent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.DimensionAvg;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.SetScoreCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.EvaluationCriterion;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentEventDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentScoreDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.criterion.ICriterionDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentScoreServiceTest {

    @Mock private IAssessmentScoreDomain scoreDomain;
    @Mock private IAssessmentEventDomain eventDomain;
    @Mock private ICriterionDomain criterionDomain;
    @Mock private IScoreDomain academicScoreDomain;
    @Mock private IClassGroupDomain classGroupDomain;
    @InjectMocks private AssessmentScoreService service;

    private final UUID enrollment = UUID.randomUUID();
    private final UUID classGroup = UUID.randomUUID();
    private final UUID author = UUID.randomUUID();

    private AssessmentEvent event(UUID id, String dimension) {
        return new AssessmentEvent(id, UUID.randomUUID(), classGroup, 1, dimension, "Tema 1");
    }

    private EvaluationCriterion directCriterion(UUID id, String dimension) {
        return new EvaluationCriterion(id, classGroup, 1, dimension, "Participacion", null, null);
    }

    private EvaluationCriterion activityCriterion(UUID id, String dimension) {
        return new EvaluationCriterion(
            id, classGroup, 1, dimension, "Evaluacion de cuadernos", "Revision de Cuadernos", null);
    }

    private void sameCourse() {
        UUID course = UUID.randomUUID();
        when(scoreDomain.courseOfCourseEnrollment(enrollment)).thenReturn(course);
        when(classGroupDomain.courseIdOfClassGroup(classGroup)).thenReturn(course);
    }

    private SetScoreCommand onEvent(UUID eventId, String score) {
        return new SetScoreCommand(enrollment, eventId, null, new BigDecimal(score), author);
    }

    private SetScoreCommand onCriterion(UUID criterionId, String score) {
        return new SetScoreCommand(enrollment, null, criterionId, new BigDecimal(score), author);
    }

    @Test
    void setScore_onActivityItem_consolidatesTheDimension() {
        UUID eventId = UUID.randomUUID();
        UUID academicScoreId = UUID.randomUUID();
        when(eventDomain.findById(eventId)).thenReturn(Optional.of(event(eventId, "Knowing")));
        sameCourse();
        when(scoreDomain.upsertForEvent(enrollment, eventId, new BigDecimal("40")))
            .thenReturn(new AssessmentScore(
                UUID.randomUUID(), enrollment, eventId, null, new BigDecimal("40"), null, null));
        when(scoreDomain.dimensionAverages(enrollment, classGroup, 1)).thenReturn(List.of(
            new DimensionAvg("Knowing", new BigDecimal("21.6666"))));
        when(academicScoreDomain.ensureAcademicScore(eq(enrollment), eq(classGroup), eq(1), any()))
            .thenReturn(academicScoreId);

        service.setScore(onEvent(eventId, "40"));

        ArgumentCaptor<BigDecimal> knowing = ArgumentCaptor.forClass(BigDecimal.class);
        verify(academicScoreDomain).setDimensions(eq(academicScoreId), any(), knowing.capture(), any(), any());
        assertThat(knowing.getValue()).isEqualByComparingTo("21.67");
    }

    @Test
    void setScore_onDirectCriterion_writesThroughTheCriterionAndConsolidates() {
        UUID criterionId = UUID.randomUUID();
        UUID academicScoreId = UUID.randomUUID();
        when(criterionDomain.findById(criterionId))
            .thenReturn(Optional.of(directCriterion(criterionId, "Doing")));
        sameCourse();
        when(scoreDomain.upsertForCriterion(enrollment, criterionId, new BigDecimal("35")))
            .thenReturn(new AssessmentScore(
                UUID.randomUUID(), enrollment, null, criterionId, new BigDecimal("35"), null, null));
        when(scoreDomain.dimensionAverages(enrollment, classGroup, 1)).thenReturn(List.of(
            new DimensionAvg("Doing", new BigDecimal("33.5"))));
        when(academicScoreDomain.ensureAcademicScore(eq(enrollment), eq(classGroup), eq(1), any()))
            .thenReturn(academicScoreId);

        service.setScore(onCriterion(criterionId, "35"));

        ArgumentCaptor<BigDecimal> doing = ArgumentCaptor.forClass(BigDecimal.class);
        verify(academicScoreDomain).setDimensions(eq(academicScoreId), any(), any(), doing.capture(), any());
        assertThat(doing.getValue()).isEqualByComparingTo("33.5");
        verify(scoreDomain, never()).upsertForEvent(any(), any(), any());
    }

    @Test
    void setScore_onActivityBackedCriterion_throws() {
        UUID criterionId = UUID.randomUUID();
        when(criterionDomain.findById(criterionId))
            .thenReturn(Optional.of(activityCriterion(criterionId, "Doing")));

        assertThatThrownBy(() -> service.setScore(onCriterion(criterionId, "35")))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Revision de Cuadernos");
        verify(scoreDomain, never()).upsertForCriterion(any(), any(), any());
    }

    /**
     * Criteria created before activity_name existed hold items with a null name. Guarding on the
     * name alone would let them take a direct score on top of the scores their items already
     * carry, leaving two answers for the same criterion.
     */
    @Test
    void setScore_onLegacyCriterionThatAlreadyHasItems_throws() {
        UUID criterionId = UUID.randomUUID();
        when(criterionDomain.findById(criterionId))
            .thenReturn(Optional.of(directCriterion(criterionId, "Doing")));
        when(eventDomain.hasItems(criterionId)).thenReturn(true);

        assertThatThrownBy(() -> service.setScore(onCriterion(criterionId, "35")))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("ya tiene criterios de actividad");
        verify(scoreDomain, never()).upsertForCriterion(any(), any(), any());
    }

    @Test
    void setScore_withBothTargets_throws() {
        SetScoreCommand both = new SetScoreCommand(
            enrollment, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10"), author);

        assertThatThrownBy(() -> service.setScore(both))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("exactamente un destino");
    }

    @Test
    void setScore_withNoTarget_throws() {
        SetScoreCommand none = new SetScoreCommand(
            enrollment, null, null, new BigDecimal("10"), author);

        assertThatThrownBy(() -> service.setScore(none))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("exactamente un destino");
    }

    @Test
    void setScore_aboveDimensionCap_throws() {
        UUID eventId = UUID.randomUUID();
        // Being max 10, nota 11 invalida
        when(eventDomain.findById(eventId)).thenReturn(Optional.of(event(eventId, "Being")));
        sameCourse();

        assertThatThrownBy(() -> service.setScore(onEvent(eventId, "11")))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void setScore_negative_throws() {
        UUID eventId = UUID.randomUUID();
        when(eventDomain.findById(eventId)).thenReturn(Optional.of(event(eventId, "Doing")));
        sameCourse();

        assertThatThrownBy(() -> service.setScore(onEvent(eventId, "-1")))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void setScore_onDirectCriterion_aboveDimensionCap_throws() {
        UUID criterionId = UUID.randomUUID();
        when(criterionDomain.findById(criterionId))
            .thenReturn(Optional.of(directCriterion(criterionId, "Deciding")));
        sameCourse();

        // Deciding max 5
        assertThatThrownBy(() -> service.setScore(onCriterion(criterionId, "6")))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void setScore_wrongCourse_throws() {
        UUID eventId = UUID.randomUUID();
        when(eventDomain.findById(eventId)).thenReturn(Optional.of(event(eventId, "Doing")));
        when(scoreDomain.courseOfCourseEnrollment(enrollment)).thenReturn(UUID.randomUUID());
        when(classGroupDomain.courseIdOfClassGroup(classGroup)).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> service.setScore(onEvent(eventId, "30")))
            .isInstanceOf(ValidationException.class);
    }

    /**
     * Deleting only locates the target, it does not re-apply the write guards. A direct score
     * sitting on a criterion that also declares an activity is exactly the inconsistency deletion
     * is meant to clean up; refusing it would strand the row with no way out.
     */
    @Test
    void delete_ofADirectScoreOnAnActivityCriterion_succeeds() {
        UUID scoreId = UUID.randomUUID();
        UUID criterionId = UUID.randomUUID();
        UUID academicScoreId = UUID.randomUUID();
        when(scoreDomain.findById(scoreId)).thenReturn(Optional.of(new AssessmentScore(
            scoreId, enrollment, null, criterionId, new BigDecimal("30"), null, null)));
        when(criterionDomain.findById(criterionId))
            .thenReturn(Optional.of(activityCriterion(criterionId, "Doing")));
        when(scoreDomain.dimensionAverages(enrollment, classGroup, 1)).thenReturn(List.of());
        when(academicScoreDomain.ensureAcademicScore(eq(enrollment), eq(classGroup), eq(1), any()))
            .thenReturn(academicScoreId);

        service.delete(scoreId);

        verify(scoreDomain).deleteById(scoreId);
    }

    @Test
    void delete_ofDirectCriterionScore_reconsolidatesThroughTheCriterion() {
        UUID scoreId = UUID.randomUUID();
        UUID criterionId = UUID.randomUUID();
        UUID academicScoreId = UUID.randomUUID();
        when(scoreDomain.findById(scoreId)).thenReturn(Optional.of(new AssessmentScore(
            scoreId, enrollment, null, criterionId, new BigDecimal("35"), null, null)));
        when(criterionDomain.findById(criterionId))
            .thenReturn(Optional.of(directCriterion(criterionId, "Doing")));
        when(scoreDomain.dimensionAverages(enrollment, classGroup, 1)).thenReturn(List.of());
        when(academicScoreDomain.ensureAcademicScore(eq(enrollment), eq(classGroup), eq(1), any()))
            .thenReturn(academicScoreId);

        service.delete(scoreId);

        verify(scoreDomain).deleteById(scoreId);
        verify(academicScoreDomain).setDimensions(academicScoreId,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
