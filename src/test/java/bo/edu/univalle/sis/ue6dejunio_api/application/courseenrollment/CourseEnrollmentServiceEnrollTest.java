package bo.edu.univalle.sis.ue6dejunio_api.application.courseenrollment;

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

import bo.edu.univalle.sis.ue6dejunio_api.application.services.courseenrollment.CourseEnrollmentService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollToCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.CreateStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentStatusChange;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentDomain;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The roster arrives as a whole PDF at once, so what matters here is that a class of forty costs a
 * fixed number of queries, and that the counts it reports stay true.
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
        return new CreateStudentCommand(
                rude, idCard, "Ana", "Perez", LocalDate.of(2015, 3, 1), "F");
    }

    private Student student(UUID id, String rude, String idCard) {
        return Student.builder()
                .id(id)
                .rudeCode(rude)
                .identityCard(idCard)
                .status(STATUS_EFFECTIVE)
                .build();
    }

    /** Whoever is importing the roster. It is who the readmission is recorded against. */
    private final UUID actor = UUID.randomUUID();

    private EnrollToCourseCommand cmd(UUID course, List<CreateStudentCommand> students) {
        return new EnrollToCourseCommand(course, students, actor);
    }

    private static final String STATUS_EFFECTIVE = "Effective";
    private static final String STATUS_WITHDRAWN = "Withdrawn";

    private Student withdrawn(UUID id, String rude, String idCard) {
        return Student.builder()
                .id(id)
                .rudeCode(rude)
                .identityCard(idCard)
                .status(STATUS_WITHDRAWN)
                .build();
    }

    /**
     * A student who left and turns up on a roster again is being readmitted, and the school says so
     * by importing them. Left alone, `students.status` stayed 'Withdrawn' while a live enrolment
     * said otherwise: the teacher saw them in the course and the directory did not, and nobody had
     * any way to tell which of the two was lying.
     */
    @Test
    void withdrawnStudent_backOnARoster_isPutBackOnTheRoll() {
        UUID id = UUID.randomUUID();
        when(studentDomain.findByRudeCodeIn(anyCollection()))
                .thenReturn(List.of(withdrawn(id, "R1", "C1")));
        when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of());
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of());

        EnrollResult result = service.enroll(cmd(courseId, List.of(row("R1", "C1"))));

        ArgumentCaptor<StudentStatusChange> change =
                ArgumentCaptor.forClass(StudentStatusChange.class);
        verify(studentDomain).updateStatusIn(eq(Set.of(id)), change.capture());
        assertThat(change.getValue().status()).isEqualTo(STATUS_EFFECTIVE);
        assertThat(change.getValue().changedBy()).isEqualTo(actor);
        assertThat(result.studentsReadmitted()).isEqualTo(1);
    }

    /**
     * The withdrawal note explained an absence that is over. Kept, it would sit under a student who
     * is on the roll saying they left, and the panel that reads it has no way to know it is stale.
     */
    @Test
    void readmission_doesNotKeepTheOldExplanation() {
        UUID id = UUID.randomUUID();
        when(studentDomain.findByRudeCodeIn(anyCollection()))
                .thenReturn(List.of(withdrawn(id, "R1", "C1")));
        when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of());
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of());

        service.enroll(cmd(courseId, List.of(row("R1", "C1"))));

        ArgumentCaptor<StudentStatusChange> change =
                ArgumentCaptor.forClass(StudentStatusChange.class);
        verify(studentDomain).updateStatusIn(eq(Set.of(id)), change.capture());
        assertThat(change.getValue().reason()).isNull();
        assertThat(change.getValue().note()).isNull();
    }

    /**
     * Nobody to put back means nobody's status is rewritten — the set the write is given is empty.
     */
    @Test
    void studentAlreadyOnTheRoll_hasTheirStatusLeftAlone() {
        UUID id = UUID.randomUUID();
        when(studentDomain.findByRudeCodeIn(anyCollection()))
                .thenReturn(List.of(student(id, "R1", "C1")));
        when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of());
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of());

        EnrollResult result = service.enroll(cmd(courseId, List.of(row("R1", "C1"))));

        verify(studentDomain).updateStatusIn(eq(Set.of()), any());
        assertThat(result.studentsReadmitted()).isZero();
    }

    /** A PDF that repeats a row must not write the same readmission twice. */
    @Test
    void withdrawnStudentTwiceInOneFile_isReadmittedOnce() {
        UUID id = UUID.randomUUID();
        when(studentDomain.findByRudeCodeIn(anyCollection()))
                .thenReturn(List.of(withdrawn(id, "R1", "C1")));
        when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of());
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of());

        EnrollResult result =
                service.enroll(cmd(courseId, List.of(row("R1", "C1"), row("R1", "C1"))));

        verify(studentDomain, times(1)).updateStatusIn(eq(Set.of(id)), any());
        assertThat(result.studentsReadmitted()).isEqualTo(1);
    }

    /**
     * The seat is decided by the enrolments still in force, not by every one that ever existed.
     * Counting a closed enrolment as a seat taken made the import report the student as skipped and
     * quietly leave them out of the course they were just re-registered into — and the unique index
     * on (student, course) means the way back in is to reopen that row, not to add a second one.
     */
    @Test
    void closedEnrolment_doesNotCountAsASeatTaken() {
        UUID id = UUID.randomUUID();
        when(studentDomain.findByRudeCodeIn(anyCollection()))
                .thenReturn(List.of(withdrawn(id, "R1", "C1")));
        when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of());
        // The student holds a closed enrolment in this very course. It is a record of them leaving,
        // not a seat still taken, and the unique index means it is also the only way back in.
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of(id, STATUS_WITHDRAWN));

        EnrollResult result = service.enroll(cmd(courseId, List.of(row("R1", "C1"))));

        verify(enrollmentDomain).reactivateEnrollments(courseId, Set.of(id));
        verify(enrollmentDomain, never()).saveEnrollment(any(), any());
        assertThat(result.enrollmentsCreated()).isEqualTo(1);
        assertThat(result.enrollmentsSkipped()).isZero();
    }

    /**
     * The whole roster reopens in one statement. Asking per student would put back the per-row
     * round trip that batching the three lookups above exists to avoid.
     */
    @Test
    void severalClosedEnrolments_areReopenedInOneCall() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(studentDomain.findByRudeCodeIn(anyCollection()))
                .thenReturn(List.of(withdrawn(first, "R1", "C1"), withdrawn(second, "R2", "C2")));
        when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of());
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of(first, STATUS_WITHDRAWN, second, STATUS_WITHDRAWN));

        service.enroll(cmd(courseId, List.of(row("R1", "C1"), row("R2", "C2"))));

        verify(enrollmentDomain, times(1)).reactivateEnrollments(courseId, Set.of(first, second));
    }

    @Test
    void unknownCourse_throwsBeforeTouchingAnyStudent() {
        UUID missing = UUID.randomUUID();
        when(enrollmentDomain.courseExists(missing)).thenReturn(false);

        assertThatThrownBy(() -> service.enroll(cmd(missing, List.of(row("R1", "C1")))))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(studentDomain, never()).save(any());
    }

    @Test
    void newStudent_isCreatedAndEnrolled() {
        when(studentDomain.findByRudeCodeIn(anyCollection())).thenReturn(List.of());
        when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of());
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of());
        UUID newId = UUID.randomUUID();
        when(studentDomain.save(any())).thenReturn(student(newId, "R1", "C1"));

        EnrollResult result = service.enroll(cmd(courseId, List.of(row("R1", "C1"))));

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
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of());

        EnrollResult result = service.enroll(cmd(courseId, List.of(row("R1", "C1"))));

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
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of());

        EnrollResult result = service.enroll(cmd(courseId, List.of(row("R1", "C1"))));

        assertThat(result.studentsExisting()).isEqualTo(1);
        verify(studentDomain, never()).save(any());
    }

    @Test
    void studentAlreadyEnrolledInThisCourse_isSkipped() {
        UUID existingId = UUID.randomUUID();
        when(studentDomain.findByRudeCodeIn(anyCollection()))
                .thenReturn(List.of(student(existingId, "R1", "C1")));
        when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of());
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of(existingId, STATUS_EFFECTIVE));

        EnrollResult result = service.enroll(cmd(courseId, List.of(row("R1", "C1"))));

        assertThat(result.enrollmentsSkipped()).isEqualTo(1);
        assertThat(result.enrollmentsCreated()).isZero();
        verify(enrollmentDomain, never()).saveEnrollment(any(), any());
    }

    @Test
    void theSameStudentTwiceInOnePayload_isCreatedOnceAndEnrolledOnce() {
        when(studentDomain.findByRudeCodeIn(anyCollection())).thenReturn(List.of());
        when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of());
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of());
        UUID newId = UUID.randomUUID();
        when(studentDomain.save(any())).thenReturn(student(newId, "R1", "C1"));

        EnrollResult result =
                service.enroll(cmd(courseId, List.of(row("R1", "C1"), row("R1", "C1"))));

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
        List<CreateStudentCommand> roster =
                List.of(row("R1", "C1"), row("R2", "C2"), row("R3", "C3"), row("R4", "C4"));
        when(studentDomain.findByRudeCodeIn(anyCollection())).thenReturn(List.of());
        when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of());
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of());
        when(studentDomain.save(any())).thenAnswer(i -> student(UUID.randomUUID(), "R", "C"));

        service.enroll(cmd(courseId, roster));

        // Three lookups for the whole roster, not three per student.
        verify(studentDomain, times(1)).findByRudeCodeIn(anyCollection());
        verify(studentDomain, times(1)).findByIdentityCardIn(anyCollection());
        verify(enrollmentDomain, times(1)).enrollmentStatusByStudent(eq(courseId), anyCollection());
    }
}
