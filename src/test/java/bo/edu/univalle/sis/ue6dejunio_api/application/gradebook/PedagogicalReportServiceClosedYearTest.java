package bo.edu.univalle.sis.ue6dejunio_api.application.gradebook;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.gradebook.PedagogicalReportService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PedagogicalReportDraft;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.gradebook.IPedagogicalReportDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreDomain;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PedagogicalReportServiceClosedYearTest {

    @Mock private IPedagogicalReportDomain reports;
    @Mock private ICourseService courseService;
    @Mock private ICourseEnrollmentDomain enrollmentDomain;
    @Mock private IScoreDomain scoreDomain;
    @Mock private ClosedYearGuard closedYearGuard;

    @Test
    void save_inAClosedYear_isRefusedBeforeTheReportIsWritten() {
        UUID courseId = UUID.randomUUID();
        when(courseService.getById(courseId))
                .thenReturn(
                        new Course(
                                courseId, 1, "Primero", 1, "A", 1, 2026, null, null, true, false));
        doThrow(new ConflictException("La gestión 2026 está cerrada; no admite cambios."))
                .when(closedYearGuard)
                .requireOpenForCourse(courseId);
        PedagogicalReportService service =
                new PedagogicalReportService(
                        reports, courseService, enrollmentDomain, scoreDomain, closedYearGuard);

        assertThatThrownBy(
                        () ->
                                service.save(
                                        courseId,
                                        1,
                                        new PedagogicalReportDraft("Logros", null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cerrada");

        verify(reports, never()).save(any(), any(Integer.class), any());
    }
}
