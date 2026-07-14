package bo.edu.univalle.sis.ue6dejunio_api.application.assessment;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.assessment.AssessmentScoreService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentEvent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.DimensionAvg;
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

    private AssessmentEvent event(UUID id, UUID cg, String dim) {
        return new AssessmentEvent(id, UUID.randomUUID(), cg, 1, dim, "Tema 1", null, new BigDecimal("100"));
    }

    @Test
    void setScore_consolidatesSimpleAveragePerDimension() {
        UUID ce = UUID.randomUUID();
        UUID cg = UUID.randomUUID();
        UUID evId = UUID.randomUUID();
        UUID asId = UUID.randomUUID();
        UUID course = UUID.randomUUID();
        when(eventDomain.findById(evId)).thenReturn(Optional.of(event(evId, cg, "Knowing")));
        when(scoreDomain.courseOfCourseEnrollment(ce)).thenReturn(course);
        when(classGroupDomain.courseIdOfClassGroup(cg)).thenReturn(course);
        when(scoreDomain.upsert(ce, evId, new BigDecimal("40")))
            .thenReturn(new AssessmentScore(UUID.randomUUID(), ce, evId, new BigDecimal("40"), null, null));
        // Saber: notas 45,45,10,10,10,10 -> media 21.666.. -> 21.67
        when(scoreDomain.dimensionAverages(ce, cg, 1)).thenReturn(List.of(
            new DimensionAvg("Knowing", new BigDecimal("21.6666"))));
        when(academicScoreDomain.ensureAcademicScore(eq(ce), eq(cg), eq(1), any())).thenReturn(asId);

        service.setScore(new SetScoreCommand(ce, evId, new BigDecimal("40"), UUID.randomUUID()));

        ArgumentCaptor<BigDecimal> knowing = ArgumentCaptor.forClass(BigDecimal.class);
        verify(academicScoreDomain).setDimensions(eq(asId), any(), knowing.capture(), any(), any());
        assertThat(knowing.getValue()).isEqualByComparingTo("21.67");
    }

    @Test
    void setScore_aboveDimensionCap_throws() {
        UUID ce = UUID.randomUUID();
        UUID cg = UUID.randomUUID();
        UUID evId = UUID.randomUUID();
        UUID course = UUID.randomUUID();
        // Being max 10, nota 11 invalida
        when(eventDomain.findById(evId)).thenReturn(Optional.of(event(evId, cg, "Being")));
        when(scoreDomain.courseOfCourseEnrollment(ce)).thenReturn(course);
        when(classGroupDomain.courseIdOfClassGroup(cg)).thenReturn(course);
        assertThatThrownBy(() -> service.setScore(
            new SetScoreCommand(ce, evId, new BigDecimal("11"), UUID.randomUUID())))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setScore_negative_throws() {
        UUID ce = UUID.randomUUID();
        UUID cg = UUID.randomUUID();
        UUID evId = UUID.randomUUID();
        UUID course = UUID.randomUUID();
        when(eventDomain.findById(evId)).thenReturn(Optional.of(event(evId, cg, "Doing")));
        when(scoreDomain.courseOfCourseEnrollment(ce)).thenReturn(course);
        when(classGroupDomain.courseIdOfClassGroup(cg)).thenReturn(course);
        assertThatThrownBy(() -> service.setScore(
            new SetScoreCommand(ce, evId, new BigDecimal("-1"), UUID.randomUUID())))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setScore_wrongCourse_throws() {
        UUID ce = UUID.randomUUID();
        UUID cg = UUID.randomUUID();
        UUID evId = UUID.randomUUID();
        when(eventDomain.findById(evId)).thenReturn(Optional.of(event(evId, cg, "Doing")));
        when(scoreDomain.courseOfCourseEnrollment(ce)).thenReturn(UUID.randomUUID());
        when(classGroupDomain.courseIdOfClassGroup(cg)).thenReturn(UUID.randomUUID());
        assertThatThrownBy(() -> service.setScore(
            new SetScoreCommand(ce, evId, new BigDecimal("30"), UUID.randomUUID())))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
