package bo.edu.univalle.sis.ue6dejunio_api.application.assessment;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.assessment.AssessmentScoreService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentEvent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.CriterionAvg;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.SetScoreCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentEventDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentScoreDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentScoreServiceTest {

    @Mock private IAssessmentScoreDomain scoreDomain;
    @Mock private IAssessmentEventDomain eventDomain;
    @Mock private IScoreDomain academicScoreDomain;
    @Mock private IClassGroupDomain classGroupDomain;
    @InjectMocks private AssessmentScoreService service;

    private AssessmentEvent event(UUID id, UUID cg, String dim, String max) {
        return new AssessmentEvent(id, UUID.randomUUID(), cg, 1, dim, "Tema 1", null, new BigDecimal(max));
    }

    @Test
    void setScore_consolidatesWeighted() {
        UUID ce = UUID.randomUUID();
        UUID cg = UUID.randomUUID();
        UUID evId = UUID.randomUUID();
        UUID asId = UUID.randomUUID();
        UUID course = UUID.randomUUID();
        when(eventDomain.findById(evId)).thenReturn(Optional.of(event(evId, cg, "Knowing", "100")));
        when(scoreDomain.courseOfCourseEnrollment(ce)).thenReturn(course);
        when(classGroupDomain.courseIdOfClassGroup(cg)).thenReturn(course);
        when(scoreDomain.upsert(ce, evId, new BigDecimal("80")))
            .thenReturn(new AssessmentScore(UUID.randomUUID(), ce, evId, new BigDecimal("80")));
        // criterion Knowing weight 45, avg 80 -> 80/100*45 = 36
        when(scoreDomain.criterionAveragesForConsolidation(ce, cg, 1)).thenReturn(List.of(
            new CriterionAvg("Knowing", new BigDecimal("45"), new BigDecimal("80"))));
        when(academicScoreDomain.ensureAcademicScore(eq(ce), eq(cg), eq(1), any())).thenReturn(asId);

        service.setScore(new SetScoreCommand(ce, evId, new BigDecimal("80"), UUID.randomUUID()));

        ArgumentCaptor<BigDecimal> knowing = ArgumentCaptor.forClass(BigDecimal.class);
        verify(academicScoreDomain).setDimensions(eq(asId), any(), knowing.capture(), any(), any());
        assertThat(knowing.getValue()).isEqualByComparingTo("36.00");
    }

    @Test
    void setScore_wrongCourse_throws() {
        UUID ce = UUID.randomUUID();
        UUID cg = UUID.randomUUID();
        UUID evId = UUID.randomUUID();
        when(eventDomain.findById(evId)).thenReturn(Optional.of(event(evId, cg, "Doing", "100")));
        when(scoreDomain.courseOfCourseEnrollment(ce)).thenReturn(UUID.randomUUID());
        when(classGroupDomain.courseIdOfClassGroup(cg)).thenReturn(UUID.randomUUID());
        assertThatThrownBy(() -> service.setScore(
            new SetScoreCommand(ce, evId, new BigDecimal("50"), UUID.randomUUID())))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setScore_aboveMax_throws() {
        UUID ce = UUID.randomUUID();
        UUID cg = UUID.randomUUID();
        UUID evId = UUID.randomUUID();
        UUID course = UUID.randomUUID();
        when(eventDomain.findById(evId)).thenReturn(Optional.of(event(evId, cg, "Being", "100")));
        when(scoreDomain.courseOfCourseEnrollment(ce)).thenReturn(course);
        when(classGroupDomain.courseIdOfClassGroup(cg)).thenReturn(course);
        assertThatThrownBy(() -> service.setScore(
            new SetScoreCommand(ce, evId, new BigDecimal("101"), UUID.randomUUID())))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
