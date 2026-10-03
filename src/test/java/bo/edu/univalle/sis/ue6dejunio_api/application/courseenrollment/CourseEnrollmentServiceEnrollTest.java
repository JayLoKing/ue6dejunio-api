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

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.courseenrollment.CourseEnrollmentService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollToCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.SkippedStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.CreateStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
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

    @Mock private ClosedYearGuard closedYearGuard;

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

    private EnrollToCourseCommand cmd(UUID course, List<CreateStudentCommand> students) {
        return new EnrollToCourseCommand(course, students);
    }

    private static final String STATUS_EFFECTIVE = "Effective";
    private static final String STATUS_WITHDRAWN = "Withdrawn";

    private Student withdrawn(UUID id, String rude, String idCard) {
        return Student.builder()
                .id(id)
                .rudeCode(rude)
                .identityCard(idCard)
                .names("Ana")
                .lastNames("Perez")
                .status(STATUS_WITHDRAWN)
                .build();
    }

    /**
     * Only the Director decides a withdrawal is over. A roster load, which a teacher can run, must
     * not undo it: the student is left out of the load and named in the result instead.
     */
    @Test
    void withdrawnStudent_onARoster_isSkippedAndReported() {
        UUID id = UUID.randomUUID();
        when(studentDomain.findByRudeCodeIn(anyCollection()))
                .thenReturn(List.of(withdrawn(id, "R1", "C1")));
        when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of());
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of());

        EnrollResult result = service.enroll(cmd(courseId, List.of(row("R1", "C1"))));

        verify(enrollmentDomain, never()).saveEnrollment(any(), any());
        verify(enrollmentDomain).reactivateEnrollments(courseId, Set.of());
        assertThat(result.skippedWithdrawn()).containsExactly(new SkippedStudent(id, "Ana Perez"));
        assertThat(result.enrollmentsCreated()).isZero();
        assertThat(result.enrollmentsSkipped()).isZero();
        assertThat(result.studentsTotal()).isEqualTo(1);
    }

    /** A withdrawn student's closed enrolment in this course must stay closed. */
    @Test
    void withdrawnStudentWithAClosedEnrolment_isNotReopened() {
        UUID id = UUID.randomUUID();
        when(studentDomain.findByRudeCodeIn(anyCollection()))
                .thenReturn(List.of(withdrawn(id, "R1", "C1")));
        when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of());
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of(id, STATUS_WITHDRAWN));

        EnrollResult result = service.enroll(cmd(courseId, List.of(row("R1", "C1"))));

        verify(enrollmentDomain).reactivateEnrollments(courseId, Set.of());
        verify(enrollmentDomain, never()).saveEnrollment(any(), any());
        assertThat(result.skippedWithdrawn()).hasSize(1);
    }

    /** The rest of the load proceeds: the withdrawn one is left out, the others are seated. */
    @Test
    void withdrawnStudent_doesNotStopTheRestOfTheLoad() {
        UUID gone = UUID.randomUUID();
        UUID here = UUID.randomUUID();
        when(studentDomain.findByRudeCodeIn(anyCollection()))
                .thenReturn(List.of(withdrawn(gone, "R1", "C1"), student(here, "R2", "C2")));
        when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of());
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of());

        EnrollResult result =
                service.enroll(cmd(courseId, List.of(row("R1", "C1"), row("R2", "C2"))));

        verify(enrollmentDomain).saveEnrollment(here, courseId);
        verify(enrollmentDomain, never()).saveEnrollment(eq(gone), any());
        assertThat(result.studentsTotal()).isEqualTo(2);
        assertThat(result.enrollmentsCreated()).isEqualTo(1);
        assertThat(result.skippedWithdrawn())
                .extracting(SkippedStudent::studentId)
                .containsExactly(gone);
    }

    /** A student on the roll reports nobody as skipped. */
    @Test
    void studentAlreadyOnTheRoll_reportsNoSkippedWithdrawn() {
        UUID id = UUID.randomUUID();
        when(studentDomain.findByRudeCodeIn(anyCollection()))
                .thenReturn(List.of(student(id, "R1", "C1")));
        when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of());
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of());

        EnrollResult result = service.enroll(cmd(courseId, List.of(row("R1", "C1"))));

        assertThat(result.skippedWithdrawn()).isEmpty();
    }

    /** A PDF that repeats a row must not list the same student twice. */
    @Test
    void withdrawnStudentTwiceInOneFile_isReportedOnce() {
        UUID id = UUID.randomUUID();
        when(studentDomain.findByRudeCodeIn(anyCollection()))
                .thenReturn(List.of(withdrawn(id, "R1", "C1")));
        when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of());
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of());

        EnrollResult result =
                service.enroll(cmd(courseId, List.of(row("R1", "C1"), row("R1", "C1"))));

        assertThat(result.skippedWithdrawn()).hasSize(1);
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
                .thenReturn(List.of(student(id, "R1", "C1")));
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
                .thenReturn(List.of(student(first, "R1", "C1"), student(second, "R2", "C2")));
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
