package bo.edu.univalle.sis.ue6dejunio_api.application.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.student.StudentService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollmentSeat;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.ReinstateResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.ReinstateStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentReinstated;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentStatusChange;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.event.IDomainEventPublisher;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentDomain;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentServiceReinstateTest {

    private static final Integer YEAR = 7;

    @Mock private IStudentDomain studentDomain;
    @Mock private ICourseEnrollmentDomain courseEnrollmentDomain;
    @Mock private IDomainEventPublisher events;
    @Mock private IAcademicYearDomain academicYearDomain;
    @Mock private ICourseDomain courseDomain;
    @Mock private ClosedYearGuard closedYearGuard;
    @InjectMocks private StudentService studentService;

    private final UUID studentId = UUID.randomUUID();
    private final UUID director = UUID.randomUUID();

    private void studentIs(String status) {
        when(studentDomain.findById(studentId))
                .thenReturn(Optional.of(Student.builder().id(studentId).status(status).build()));
    }

    private static Course courseOf(UUID id, Integer yearId) {
        return new Course(id, 1, "Primero", 1, "A", yearId, 2026, null, null, true, false);
    }

    private ReinstateResult reinstate(UUID courseId) {
        return studentService.reinstate(new ReinstateStudentCommand(studentId, courseId, director));
    }

    @Test
    void reinstate_unknownStudent_throwsNotFound() {
        when(studentDomain.findById(studentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reinstate(null)).isInstanceOf(ResourceNotFoundException.class);
        verify(studentDomain, never()).updateStatus(any(), any());
    }

    @Test
    void reinstate_studentNotWithdrawn_throwsConflict() {
        studentIs("Effective");

        assertThatThrownBy(() -> reinstate(null)).isInstanceOf(ConflictException.class);
        verify(studentDomain, never()).updateStatus(any(), any());
    }

    @Test
    void reinstate_closedYear_isRefusedBeforeAnyWrite() {
        studentIs("Withdrawn");
        when(academicYearDomain.currentYearId()).thenReturn(YEAR);
        doThrow(new ConflictException("cerrada")).when(closedYearGuard).requireOpenForYear(YEAR);

        assertThatThrownBy(() -> reinstate(UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);
        verify(studentDomain, never()).updateStatus(any(), any());
        verify(courseEnrollmentDomain, never()).saveEnrollment(any(), any());
    }

    @Test
    void reinstate_withAWithdrawnSeatThisYear_reactivatesItAndRestoresTheStudent() {
        UUID seatCourse = UUID.randomUUID();
        studentIs("Withdrawn");
        when(academicYearDomain.currentYearId()).thenReturn(YEAR);
        when(courseEnrollmentDomain.seatsOfStudentInYear(studentId, YEAR))
                .thenReturn(List.of(new EnrollmentSeat(seatCourse, "Withdrawn")));

        ReinstateResult result = reinstate(null);

        verify(courseEnrollmentDomain).reactivateEnrollments(seatCourse, Set.of(studentId));
        verify(courseEnrollmentDomain, never()).saveEnrollment(any(), any());
        assertThat(result).isEqualTo(new ReinstateResult(studentId, "Effective", seatCourse, true));

        ArgumentCaptor<StudentStatusChange> change =
                ArgumentCaptor.forClass(StudentStatusChange.class);
        verify(studentDomain).updateStatus(eq(studentId), change.capture());
        assertThat(change.getValue())
                .isEqualTo(new StudentStatusChange("Effective", null, null, director));
    }

    @Test
    void reinstate_withASeatAndTheSameCourse_isFine() {
        UUID seatCourse = UUID.randomUUID();
        studentIs("Withdrawn");
        when(academicYearDomain.currentYearId()).thenReturn(YEAR);
        when(courseEnrollmentDomain.seatsOfStudentInYear(studentId, YEAR))
                .thenReturn(List.of(new EnrollmentSeat(seatCourse, "Withdrawn")));

        ReinstateResult result = reinstate(seatCourse);

        assertThat(result.enrollmentReactivated()).isTrue();
        assertThat(result.courseId()).isEqualTo(seatCourse);
    }

    @Test
    void reinstate_withASeatAndAnotherCourse_isRefusedAsValidation() {
        UUID seatCourse = UUID.randomUUID();
        studentIs("Withdrawn");
        when(academicYearDomain.currentYearId()).thenReturn(YEAR);
        when(courseEnrollmentDomain.seatsOfStudentInYear(studentId, YEAR))
                .thenReturn(List.of(new EnrollmentSeat(seatCourse, "Withdrawn")));
        when(courseDomain.findById(seatCourse)).thenReturn(Optional.of(courseOf(seatCourse, YEAR)));

        assertThatThrownBy(() -> reinstate(UUID.randomUUID()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Primero");
        verify(studentDomain, never()).updateStatus(any(), any());
    }

    @Test
    void reinstate_withoutASeatAndNoCourse_isRefusedAsValidation() {
        studentIs("Withdrawn");
        when(academicYearDomain.currentYearId()).thenReturn(YEAR);
        when(courseEnrollmentDomain.seatsOfStudentInYear(studentId, YEAR)).thenReturn(List.of());

        assertThatThrownBy(() -> reinstate(null)).isInstanceOf(ValidationException.class);
        verify(studentDomain, never()).updateStatus(any(), any());
    }

    @Test
    void reinstate_withoutASeatAndUnknownCourse_throwsNotFound() {
        UUID courseId = UUID.randomUUID();
        studentIs("Withdrawn");
        when(academicYearDomain.currentYearId()).thenReturn(YEAR);
        when(courseEnrollmentDomain.seatsOfStudentInYear(studentId, YEAR)).thenReturn(List.of());
        when(courseDomain.findById(courseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reinstate(courseId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reinstate_withoutASeatAndACourseOfAnotherYear_isRefusedAsValidation() {
        UUID courseId = UUID.randomUUID();
        studentIs("Withdrawn");
        when(academicYearDomain.currentYearId()).thenReturn(YEAR);
        when(courseEnrollmentDomain.seatsOfStudentInYear(studentId, YEAR)).thenReturn(List.of());
        when(courseDomain.findById(courseId)).thenReturn(Optional.of(courseOf(courseId, 3)));

        assertThatThrownBy(() -> reinstate(courseId)).isInstanceOf(ValidationException.class);
        verify(courseEnrollmentDomain, never()).saveEnrollment(any(), any());
        verify(studentDomain, never()).updateStatus(any(), any());
    }

    @Test
    void reinstate_withoutASeatAndACourseOfTheYear_createsTheEnrolment() {
        UUID courseId = UUID.randomUUID();
        studentIs("Withdrawn");
        when(academicYearDomain.currentYearId()).thenReturn(YEAR);
        when(courseEnrollmentDomain.seatsOfStudentInYear(studentId, YEAR)).thenReturn(List.of());
        when(courseDomain.findById(courseId)).thenReturn(Optional.of(courseOf(courseId, YEAR)));
        when(courseEnrollmentDomain.enrollmentStatusByStudent(courseId, Set.of(studentId)))
                .thenReturn(Map.of());

        ReinstateResult result = reinstate(courseId);

        verify(courseEnrollmentDomain).saveEnrollment(studentId, courseId);
        verify(courseEnrollmentDomain, never()).reactivateEnrollments(any(), any());
        assertThat(result).isEqualTo(new ReinstateResult(studentId, "Effective", courseId, false));
        verify(closedYearGuard).requireOpenForYear(YEAR);
        verify(studentDomain).updateStatus(any(UUID.class), any(StudentStatusChange.class));
    }

    @Test
    void reinstate_whenARowForThatCourseExists_reopensItInsteadOfInserting() {
        UUID courseId = UUID.randomUUID();
        studentIs("Withdrawn");
        when(academicYearDomain.currentYearId()).thenReturn(YEAR);
        when(courseEnrollmentDomain.seatsOfStudentInYear(studentId, YEAR)).thenReturn(List.of());
        when(courseDomain.findById(courseId)).thenReturn(Optional.of(courseOf(courseId, YEAR)));
        when(courseEnrollmentDomain.enrollmentStatusByStudent(courseId, Set.of(studentId)))
                .thenReturn(Map.of(studentId, "Withdrawn"));

        ReinstateResult result = reinstate(courseId);

        verify(courseEnrollmentDomain).reactivateEnrollments(courseId, Set.of(studentId));
        verify(courseEnrollmentDomain, never()).saveEnrollment(any(), any());
        assertThat(result.enrollmentReactivated()).isTrue();
        verify(closedYearGuard).requireOpenForYear(anyInt());
    }

    /** The fact leaves with the name and the course, so the notice reads without a lookup. */
    @Test
    void reinstate_statesTheStudentAndTheCourseTheyAreBackIn() {
        UUID courseId = UUID.randomUUID();
        when(studentDomain.findById(studentId))
                .thenReturn(
                        Optional.of(
                                Student.builder()
                                        .id(studentId)
                                        .status("Withdrawn")
                                        .names("Ana")
                                        .lastNames("Quispe")
                                        .build()));
        when(academicYearDomain.currentYearId()).thenReturn(YEAR);
        when(courseEnrollmentDomain.seatsOfStudentInYear(studentId, YEAR)).thenReturn(List.of());
        when(courseDomain.findById(courseId)).thenReturn(Optional.of(courseOf(courseId, YEAR)));
        when(courseEnrollmentDomain.enrollmentStatusByStudent(courseId, Set.of(studentId)))
                .thenReturn(Map.of());

        reinstate(courseId);

        verify(events)
                .publish(new StudentReinstated(studentId, "Ana Quispe", courseId, "Primero A"));
    }

    /** Nothing was written, so there is nothing to tell anybody about. */
    @Test
    void reinstate_refused_statesNothing() {
        studentIs("Effective");

        assertThatThrownBy(() -> reinstate(null)).isInstanceOf(ConflictException.class);

        verify(events, never()).publish(any());
    }
}
