package bo.edu.univalle.sis.ue6dejunio_api.application.courseenrollment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.courseenrollment.CourseEnrollmentService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollToCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.CreateStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentDomain;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** A roster import writes students as well as enrolments, so nothing of it may start. */
@ExtendWith(MockitoExtension.class)
class CourseEnrollmentServiceClosedYearTest {

    @Mock private IStudentDomain studentDomain;
    @Mock private ICourseEnrollmentDomain enrollmentDomain;
    @Mock private IAcademicYearDomain academicYearDomain;
    @Mock private ClosedYearGuard closedYearGuard;

    @Test
    void enroll_inAClosedYear_isRefusedBeforeAnyStudentIsLookedUpOrCreated() {
        UUID courseId = UUID.randomUUID();
        when(enrollmentDomain.courseExists(courseId)).thenReturn(true);
        doThrow(new ConflictException("La gestión 2026 está cerrada; no admite cambios."))
                .when(closedYearGuard)
                .requireOpenForCourse(courseId);
        EnrollToCourseCommand command =
                new EnrollToCourseCommand(
                        courseId,
                        List.of(
                                new CreateStudentCommand(
                                        "R-1",
                                        "1234567",
                                        "Ana",
                                        "Quispe",
                                        LocalDate.of(2012, 3, 4),
                                        "F")));

        assertThatThrownBy(
                        () ->
                                new CourseEnrollmentService(
                                                studentDomain,
                                                enrollmentDomain,
                                                academicYearDomain,
                                                closedYearGuard)
                                        .enroll(command))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cerrada");

        verifyNoInteractions(studentDomain);
        verify(enrollmentDomain, never()).saveEnrollment(any(), any());
    }
}
