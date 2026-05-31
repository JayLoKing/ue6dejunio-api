package bo.edu.univalle.sis.ue6dejunio_api.application.enrollment;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.enrollment.EnrollmentService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment.EnrollCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment.EnrollResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.CreateStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.enrollment.IEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock private IStudentDomain studentDomain;
    @Mock private IClassGroupDomain classGroupDomain;
    @Mock private IEnrollmentDomain enrollmentDomain;
    @InjectMocks private EnrollmentService enrollmentService;

    private CreateStudentCommand student() {
        return new CreateStudentCommand("RUDE1", "CI1", "Ana", "Quispe",
            LocalDate.of(2010, 1, 1), "F");
    }

    @Test
    void enrollCourse_newStudent_crossEnrollsAllClassGroups() {
        UUID cg1 = UUID.randomUUID();
        UUID cg2 = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(classGroupDomain.currentAcademicYearId()).thenReturn(1);
        when(classGroupDomain.classGroupIdsByCourse(1, 1, 1)).thenReturn(List.of(cg1, cg2));
        when(studentDomain.findByRudeCode("RUDE1")).thenReturn(Optional.empty());
        when(studentDomain.findByIdentityCard("CI1")).thenReturn(Optional.empty());
        when(studentDomain.save(any(Student.class)))
            .thenReturn(Student.builder().id(studentId).build());
        when(enrollmentDomain.existsEnrollment(eq(studentId), any())).thenReturn(false);

        EnrollResult r = enrollmentService.enrollCourse(
            new EnrollCourseCommand(1, 1, List.of(student())));

        assertThat(r.studentsCreated()).isEqualTo(1);
        assertThat(r.classGroupsInCourse()).isEqualTo(2);
        assertThat(r.enrollmentsCreated()).isEqualTo(2);
        verify(enrollmentDomain, times(2)).saveEnrollment(eq(studentId), any());
    }

    @Test
    void enrollCourse_existingStudent_skipsExistingEnrollment() {
        UUID cg1 = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(classGroupDomain.currentAcademicYearId()).thenReturn(1);
        when(classGroupDomain.classGroupIdsByCourse(1, 1, 1)).thenReturn(List.of(cg1));
        when(studentDomain.findByRudeCode("RUDE1"))
            .thenReturn(Optional.of(Student.builder().id(studentId).build()));
        when(enrollmentDomain.existsEnrollment(studentId, cg1)).thenReturn(true);

        EnrollResult r = enrollmentService.enrollCourse(
            new EnrollCourseCommand(1, 1, List.of(student())));

        assertThat(r.studentsExisting()).isEqualTo(1);
        assertThat(r.enrollmentsSkipped()).isEqualTo(1);
        assertThat(r.enrollmentsCreated()).isZero();
    }

    @Test
    void enrollCourse_noClassGroups_throws() {
        when(classGroupDomain.currentAcademicYearId()).thenReturn(1);
        when(classGroupDomain.classGroupIdsByCourse(1, 1, 1)).thenReturn(List.of());
        assertThatThrownBy(() -> enrollmentService.enrollCourse(
            new EnrollCourseCommand(1, 1, List.of(student()))))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
