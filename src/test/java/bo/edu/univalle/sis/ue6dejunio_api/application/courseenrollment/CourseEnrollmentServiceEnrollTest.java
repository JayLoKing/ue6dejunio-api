package bo.edu.univalle.sis.ue6dejunio_api.application.courseenrollment;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.courseenrollment.CourseEnrollmentService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollToCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.CreateStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentDomain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The roster arrives as a whole PDF at once, so what matters here is that a class of forty costs
 * a fixed number of queries, and that the counts it reports stay true.
 */
@ExtendWith(MockitoExtension.class)
class CourseEnrollmentServiceEnrollTest {

    @Mock private IStudentDomain studentDomain;
    @Mock private ICourseEnrollmentDomain enrollmentDomain;

    @InjectMocks private CourseEnrollmentService service;

    private final UUID courseId = UUID.randomUUID();

    @BeforeEach
    void courseExists() {
        // Lenient: the unknown-course test asks about a different id and never reaches this stub.
        lenient().when(enrollmentDomain.courseExists(courseId)).thenReturn(true);
    }

    private CreateStudentCommand row(String rude, String idCard) {
        return new CreateStudentCommand(rude, idCard, "Ana", "Perez",
            LocalDate.of(2015, 3, 1), "F");
    }

    private Student student(UUID id, String rude, String idCard) {
        return Student.builder().id(id).rudeCode(rude).identityCard(idCard).build();
    }

    @Test
    void unknownCourse_throwsBeforeTouchingAnyStudent() {
        UUID missing = UUID.randomUUID();
        when(enrollmentDomain.courseExists(missing)).thenReturn(false);

        assertThatThrownBy(() -> service.enroll(new EnrollToCourseCommand(missing, List.of(row("R1", "C1")))))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(studentDomain, never()).save(any());
    }

    @Test
    void newStudent_isCreatedAndEnrolled() {
        when(studentDomain.findByRudeCodeIn(anyCollection())).thenReturn(List.of());
        when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of());
        when(enrollmentDomain.enrolledStudentIds(eq(courseId), anyCollection())).thenReturn(List.of());
        UUID newId = UUID.randomUUID();
        when(studentDomain.save(any())).thenReturn(student(newId, "R1", "C1"));

        EnrollResult result = service.enroll(new EnrollToCourseCommand(courseId, List.of(row("R1", "C1"))));

        assertThat(result.studentsCreated()).isEqualTo(1);
        assertThat(result.studentsExisting()).isZero();
        assertThat(result.enrollmentsCreated()).isEqualTo(1);
        assertThat(result.enrollmentsSkipped()).isZero();
        verify(enrollmentDomain).saveEnrollment(newId, courseId);
    }

    @Test
    void studentAlreadyOnRecordByRude_isReusedNotRecreated() {
        UUID existingId = UUID.randomUUID();
        when(studentDomain.findByRudeCodeIn(anyCollection()))
            .thenReturn(List.of(student(existingId, "R1", "C1")));
        when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of());
        when(enrollmentDomain.enrolledStudentIds(eq(courseId), anyCollection())).thenReturn(List.of());

        EnrollResult result = service.enroll(new EnrollToCourseCommand(courseId, List.of(row("R1", "C1"))));

        assertThat(result.studentsCreated()).isZero();
        assertThat(result.studentsExisting()).isEqualTo(1);
        assertThat(result.enrollmentsCreated()).isEqualTo(1);
        verify(studentDomain, never()).save(any());
    }

    @Test
    void studentFoundByIdentityCardWhenTheRudeCodeMisses_isReused() {
        UUID existingId = UUID.randomUUID();
        when(studentDomain.findByRudeCodeIn(anyCollection())).thenReturn(List.of());
        when(studentDomain.findByIdentityCardIn(anyCollection()))
            .thenReturn(List.of(student(existingId, "OTRO", "C1")));
        when(enrollmentDomain.enrolledStudentIds(eq(courseId), anyCollection())).thenReturn(List.of());

        EnrollResult result = service.enroll(new EnrollToCourseCommand(courseId, List.of(row("R1", "C1"))));

        assertThat(result.studentsExisting()).isEqualTo(1);
        verify(studentDomain, never()).save(any());
    }

    @Test
    void studentAlreadyEnrolledInThisCourse_isSkipped() {
        UUID existingId = UUID.randomUUID();
        when(studentDomain.findByRudeCodeIn(anyCollection()))
            .thenReturn(List.of(student(existingId, "R1", "C1")));
        when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of());
        when(enrollmentDomain.enrolledStudentIds(eq(courseId), anyCollection()))
            .thenReturn(List.of(existingId));

        EnrollResult result = service.enroll(new EnrollToCourseCommand(courseId, List.of(row("R1", "C1"))));

        assertThat(result.enrollmentsSkipped()).isEqualTo(1);
        assertThat(result.enrollmentsCreated()).isZero();
        verify(enrollmentDomain, never()).saveEnrollment(any(), any());
    }

    @Test
    void theSameStudentTwiceInOnePayload_isCreatedOnceAndEnrolledOnce() {
        when(studentDomain.findByRudeCodeIn(anyCollection())).thenReturn(List.of());
        when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of());
        when(enrollmentDomain.enrolledStudentIds(eq(courseId), anyCollection())).thenReturn(List.of());
        UUID newId = UUID.randomUUID();
        when(studentDomain.save(any())).thenReturn(student(newId, "R1", "C1"));

        EnrollResult result = service.enroll(
            new EnrollToCourseCommand(courseId, List.of(row("R1", "C1"), row("R1", "C1"))));

        // A PDF that repeats a row must not produce two students, nor two enrolments.
        assertThat(result.studentsCreated()).isEqualTo(1);
        assertThat(result.studentsExisting()).isEqualTo(1);
        assertThat(result.enrollmentsCreated()).isEqualTo(1);
        assertThat(result.enrollmentsSkipped()).isEqualTo(1);
        verify(studentDomain, times(1)).save(any());
        verify(enrollmentDomain, times(1)).saveEnrollment(newId, courseId);
    }

    @Test
    void aWholeRoster_costsAFixedNumberOfLookups() {
        List<CreateStudentCommand> roster = List.of(
            row("R1", "C1"), row("R2", "C2"), row("R3", "C3"), row("R4", "C4"));
        when(studentDomain.findByRudeCodeIn(anyCollection())).thenReturn(List.of());
        when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of());
        when(enrollmentDomain.enrolledStudentIds(eq(courseId), anyCollection())).thenReturn(List.of());
        when(studentDomain.save(any())).thenAnswer(i -> student(UUID.randomUUID(), "R", "C"));

        service.enroll(new EnrollToCourseCommand(courseId, roster));

        // Three lookups for the whole roster, not three per student.
        verify(studentDomain, times(1)).findByRudeCodeIn(anyCollection());
        verify(studentDomain, times(1)).findByIdentityCardIn(anyCollection());
        verify(enrollmentDomain, times(1)).enrolledStudentIds(eq(courseId), anyCollection());
    }
}
