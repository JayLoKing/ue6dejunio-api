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
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollConflict;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollToCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrolledElsewhere;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.SkippedStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.YearSeat;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.CreateStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentDomain;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    @Mock private IAcademicYearDomain academicYearDomain;
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
                .names("Ana")
                .lastNames("Perez")
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

    private static final Integer PREVIOUS_YEAR = 7;

    private void rosterOf(Student... known) {
        when(studentDomain.findByRudeCodeIn(anyCollection())).thenReturn(List.of(known));
        when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of());
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of());
    }

    /**
     * Existing students split by what the previous year says about them. The question is asked once
     * for the whole load, about the year before the course's own, never per row.
     */
    @Test
    void existingStudents_splitIntoFromPreviousYearAndReentering() {
        UUID continuing = UUID.randomUUID();
        UUID returning = UUID.randomUUID();
        rosterOf(student(continuing, "R1", "C1"), student(returning, "R2", "C2"));
        when(academicYearDomain.previousYearIdOfCourse(courseId))
                .thenReturn(Optional.of(PREVIOUS_YEAR));
        when(enrollmentDomain.studentsEffectiveInYear(eq(PREVIOUS_YEAR), anyCollection()))
                .thenReturn(Set.of(continuing));

        EnrollResult result =
                service.enroll(cmd(courseId, List.of(row("R1", "C1"), row("R2", "C2"))));

        assertThat(result.studentsFromPreviousYear()).isEqualTo(1);
        assertThat(result.studentsReentering()).isEqualTo(1);
        assertThat(result.studentsCreated()).isZero();
        verify(enrollmentDomain, times(1))
                .studentsEffectiveInYear(eq(PREVIOUS_YEAR), anyCollection());
    }

    /** With no earlier gestión on record nobody can have come from it. */
    @Test
    void noPreviousYear_makesEveryExistingStudentReentering() {
        rosterOf(student(UUID.randomUUID(), "R1", "C1"));
        when(academicYearDomain.previousYearIdOfCourse(courseId)).thenReturn(Optional.empty());

        EnrollResult result = service.enroll(cmd(courseId, List.of(row("R1", "C1"))));

        assertThat(result.studentsFromPreviousYear()).isZero();
        assertThat(result.studentsReentering()).isEqualTo(1);
        verify(enrollmentDomain, never()).studentsEffectiveInYear(any(), anyCollection());
    }

    /**
     * A student created by this very load has no past: they count as created and in neither bucket,
     * and a repeated row of theirs must not slip into "reentering".
     */
    @Test
    void createdStudent_isInNeitherBucket_evenWhenTheRowRepeats() {
        when(studentDomain.findByRudeCodeIn(anyCollection())).thenReturn(List.of());
        when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of());
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of());
        when(studentDomain.save(any())).thenReturn(student(UUID.randomUUID(), "R1", "C1"));

        EnrollResult result =
                service.enroll(cmd(courseId, List.of(row("R1", "C1"), row("R1", "C1"))));

        assertThat(result.studentsCreated()).isEqualTo(1);
        assertThat(result.studentsFromPreviousYear()).isZero();
        assertThat(result.studentsReentering()).isZero();
    }

    /**
     * The four groups partition the distinct students of the request: created, from the previous
     * year, re-entering, and withdrawn (skipped). A repeated row counts once in every group.
     */
    @Test
    void theGroupsPartitionTheDistinctStudentsOfTheRequest() {
        UUID continuing = UUID.randomUUID();
        UUID returning = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        when(studentDomain.findByRudeCodeIn(anyCollection()))
                .thenReturn(
                        List.of(
                                student(continuing, "R1", "C1"),
                                student(returning, "R2", "C2"),
                                withdrawn(gone, "R3", "C3")));
        when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of());
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of());
        when(studentDomain.save(any())).thenReturn(student(UUID.randomUUID(), "R4", "C4"));
        when(academicYearDomain.previousYearIdOfCourse(courseId))
                .thenReturn(Optional.of(PREVIOUS_YEAR));
        when(enrollmentDomain.studentsEffectiveInYear(eq(PREVIOUS_YEAR), anyCollection()))
                .thenReturn(Set.of(continuing));

        EnrollResult result =
                service.enroll(
                        cmd(
                                courseId,
                                List.of(
                                        row("R1", "C1"),
                                        row("R1", "C1"),
                                        row("R2", "C2"),
                                        row("R3", "C3"),
                                        row("R3", "C3"),
                                        row("R4", "C4"))));

        assertThat(result.studentsCreated()).isEqualTo(1);
        assertThat(result.studentsFromPreviousYear()).isEqualTo(1);
        assertThat(result.studentsReentering()).isEqualTo(1);
        assertThat(result.skippedWithdrawn()).hasSize(1);
        assertThat(
                        result.studentsCreated()
                                + result.studentsFromPreviousYear()
                                + result.studentsReentering()
                                + result.skippedWithdrawn().size())
                .isEqualTo(4);
    }

    private static final UUID SIBLING_COURSE = UUID.randomUUID();

    private void seatsInYear(YearSeat... seats) {
        when(enrollmentDomain.seatsInYearOfCourse(eq(courseId), anyCollection()))
                .thenReturn(List.of(seats));
    }

    /**
     * A student already sitting in another course of the same gestión is not given a second seat:
     * the load leaves them out and says where they are, so the teacher can sort it out with the
     * school rather than discover a student on two rolls.
     */
    @Test
    void studentEffectiveInASiblingCourseOfTheYear_isSkippedAndNamedWithThatCourse() {
        UUID id = UUID.randomUUID();
        rosterOf(student(id, "R1", "C1"));
        seatsInYear(new YearSeat(id, SIBLING_COURSE, "Segundo B", STATUS_EFFECTIVE));

        EnrollResult result = service.enroll(cmd(courseId, List.of(row("R1", "C1"))));

        verify(enrollmentDomain, never()).saveEnrollment(any(), any());
        verify(enrollmentDomain).reactivateEnrollments(courseId, Set.of());
        assertThat(result.skippedOtherCourse())
                .containsExactly(new EnrolledElsewhere(id, "R1", "Ana Perez", "Segundo B"));
        assertThat(result.enrollmentsCreated()).isZero();
        assertThat(result.studentsReentering()).isZero();
        assertThat(result.studentsFromPreviousYear()).isZero();
    }

    /** A seat they left in that sibling course is no seat at all: the student is enrolled. */
    @Test
    void aWithdrawnSeatInASiblingCourse_doesNotBlockTheLoad() {
        UUID id = UUID.randomUUID();
        rosterOf(student(id, "R1", "C1"));
        seatsInYear(new YearSeat(id, SIBLING_COURSE, "Segundo B", STATUS_WITHDRAWN));

        EnrollResult result = service.enroll(cmd(courseId, List.of(row("R1", "C1"))));

        verify(enrollmentDomain).saveEnrollment(id, courseId);
        assertThat(result.skippedOtherCourse()).isEmpty();
    }

    /** The course being loaded is not "another" course: a seat there is the ordinary repeat. */
    @Test
    void aSeatInTheCourseBeingLoaded_isNotAnotherCourse() {
        UUID id = UUID.randomUUID();
        when(studentDomain.findByRudeCodeIn(anyCollection()))
                .thenReturn(List.of(student(id, "R1", "C1")));
        when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of());
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of(id, STATUS_EFFECTIVE));
        seatsInYear(new YearSeat(id, courseId, "Primero A", STATUS_EFFECTIVE));

        EnrollResult result = service.enroll(cmd(courseId, List.of(row("R1", "C1"))));

        assertThat(result.skippedOtherCourse()).isEmpty();
        assertThat(result.enrollmentsSkipped()).isEqualTo(1);
    }

    /** Repeated rows name the student once, and the year is asked about once for the whole file. */
    @Test
    void siblingSeats_areAskedOnce_andARepeatedRowIsListedOnce() {
        UUID id = UUID.randomUUID();
        rosterOf(student(id, "R1", "C1"));
        seatsInYear(new YearSeat(id, SIBLING_COURSE, "Segundo B", STATUS_EFFECTIVE));

        EnrollResult result =
                service.enroll(cmd(courseId, List.of(row("R1", "C1"), row("R1", "C1"))));

        assertThat(result.skippedOtherCourse()).hasSize(1);
        verify(enrollmentDomain, times(1)).seatsInYearOfCourse(eq(courseId), anyCollection());
    }

    private Student named(UUID id, String rude, String idCard, String names, String lastNames) {
        return Student.builder()
                .id(id)
                .rudeCode(rude)
                .identityCard(idCard)
                .names(names)
                .lastNames(lastNames)
                .status(STATUS_EFFECTIVE)
                .build();
    }

    /**
     * A row whose RUDE is one student's and whose CI is another's names nobody for certain. Taking
     * the RUDE's student would enrol them on data that half belongs to someone else, so the row is
     * left out and both are named.
     */
    @Test
    void rudeOfOneStudentAndCardOfAnother_isSkippedAsAConflict() {
        UUID byRudeId = UUID.randomUUID();
        UUID byCardId = UUID.randomUUID();
        when(studentDomain.findByRudeCodeIn(anyCollection()))
                .thenReturn(List.of(named(byRudeId, "R1", "C1", "Ana", "Perez")));
        when(studentDomain.findByIdentityCardIn(anyCollection()))
                .thenReturn(List.of(named(byCardId, "R2", "C2", "Luis", "Mamani")));
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of());

        EnrollResult result = service.enroll(cmd(courseId, List.of(row("R1", "C2"))));

        verify(enrollmentDomain, never()).saveEnrollment(any(), any());
        verify(studentDomain, never()).save(any());
        assertThat(result.conflicts()).hasSize(1);
        EnrollConflict conflict = result.conflicts().getFirst();
        assertThat(conflict.rudeCode()).isEqualTo("R1");
        assertThat(conflict.identityCard()).isEqualTo("C2");
        assertThat(conflict.fullName()).isEqualTo("Ana Perez");
        assertThat(conflict.reason()).contains("Ana Perez").contains("Luis Mamani");
        assertThat(result.studentsExisting()).isZero();
        assertThat(result.studentsReentering()).isZero();
        assertThat(result.enrollmentsCreated()).isZero();
    }

    /** The conflict costs only its own row: the rest of the file is loaded. */
    @Test
    void aConflictingRow_doesNotStopTheRestOfTheLoad() {
        UUID byRudeId = UUID.randomUUID();
        UUID byCardId = UUID.randomUUID();
        when(studentDomain.findByRudeCodeIn(anyCollection()))
                .thenReturn(List.of(named(byRudeId, "R1", "C1", "Ana", "Perez")));
        when(studentDomain.findByIdentityCardIn(anyCollection()))
                .thenReturn(List.of(named(byCardId, "R2", "C2", "Luis", "Mamani")));
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of());

        EnrollResult result =
                service.enroll(cmd(courseId, List.of(row("R1", "C2"), row("R1", "C1"))));

        verify(enrollmentDomain).saveEnrollment(byRudeId, courseId);
        assertThat(result.conflicts()).hasSize(1);
        assertThat(result.enrollmentsCreated()).isEqualTo(1);
    }

    /** The same RUDE and CI on one student is simply that student. */
    @Test
    void rudeAndCardOfTheSameStudent_isNoConflict() {
        UUID id = UUID.randomUUID();
        Student ana = named(id, "R1", "C1", "Ana", "Perez");
        when(studentDomain.findByRudeCodeIn(anyCollection())).thenReturn(List.of(ana));
        when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of(ana));
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of());

        EnrollResult result = service.enroll(cmd(courseId, List.of(row("R1", "C1"))));

        assertThat(result.conflicts()).isEmpty();
        verify(enrollmentDomain).saveEnrollment(id, courseId);
    }

    /**
     * One student typed in by hand has no summary to be listed in: a student seated elsewhere this
     * gestión is refused outright, naming the course, so the form can say why.
     */
    @Test
    void enrollOne_studentSeatedInASiblingCourse_isAConflictNamingTheCourse() {
        UUID id = UUID.randomUUID();
        rosterOf(student(id, "R1", "C1"));
        seatsInYear(new YearSeat(id, SIBLING_COURSE, "Segundo B", STATUS_EFFECTIVE));

        assertThatThrownBy(() -> service.enrollOne(cmd(courseId, List.of(row("R1", "C1")))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("El estudiante ya está inscrito en Segundo B de esta gestión.");

        verify(enrollmentDomain, never()).saveEnrollment(any(), any());
    }

    /** Likewise a RUDE and a CI of two different students: nothing is written. */
    @Test
    void enrollOne_rudeAndCardOfDifferentStudents_isAConflict() {
        when(studentDomain.findByRudeCodeIn(anyCollection()))
                .thenReturn(List.of(named(UUID.randomUUID(), "R1", "C1", "Ana", "Perez")));
        when(studentDomain.findByIdentityCardIn(anyCollection()))
                .thenReturn(List.of(named(UUID.randomUUID(), "R2", "C2", "Luis", "Mamani")));
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of());

        assertThatThrownBy(() -> service.enrollOne(cmd(courseId, List.of(row("R1", "C2")))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Ana Perez")
                .hasMessageContaining("Luis Mamani");

        verify(enrollmentDomain, never()).saveEnrollment(any(), any());
        verify(studentDomain, never()).save(any());
    }

    /** The roster load reports the same cases instead of refusing the whole file. */
    @Test
    void enroll_aRosterWithAStudentSeatedElsewhere_doesNotThrow() {
        UUID id = UUID.randomUUID();
        rosterOf(student(id, "R1", "C1"));
        seatsInYear(new YearSeat(id, SIBLING_COURSE, "Segundo B", STATUS_EFFECTIVE));

        assertThat(service.enroll(cmd(courseId, List.of(row("R1", "C1")))).skippedOtherCourse())
                .hasSize(1);
    }

    /** A withdrawn student is reported as skipped and kept out of both buckets. */
    @Test
    void withdrawnStudent_isNotCountedAsFromPreviousYearNorReentering() {
        UUID gone = UUID.randomUUID();
        rosterOf(withdrawn(gone, "R1", "C1"));

        EnrollResult result = service.enroll(cmd(courseId, List.of(row("R1", "C1"))));

        assertThat(result.studentsFromPreviousYear()).isZero();
        assertThat(result.studentsReentering()).isZero();
        verify(enrollmentDomain, never()).studentsEffectiveInYear(any(), anyCollection());
    }
}
