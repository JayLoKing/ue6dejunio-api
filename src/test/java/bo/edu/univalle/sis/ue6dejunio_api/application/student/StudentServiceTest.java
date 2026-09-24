package bo.edu.univalle.sis.ue6dejunio_api.application.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.student.StudentService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryScope;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentStatusChange;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentWithdrawalReason;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentWithdrawn;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.WithdrawStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.event.IDomainEventPublisher;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentDomain;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock private IStudentDomain studentDomain;
    @Mock private ICourseEnrollmentDomain courseEnrollmentDomain;
    @Mock private IDomainEventPublisher events;
    @Mock private IAcademicYearDomain academicYearDomain;
    @InjectMocks private StudentService studentService;

    private final PageQuery pageQuery = PageQuery.of(0, 30);
    private final PageResult<StudentDirectoryItem> emptyPage =
            new PageResult<>(List.of(), 0, 30, 0);

    @Test
    void search_delegatesToDomain() {
        UUID courseId = UUID.randomUUID();
        StudentDirectoryQuery query = StudentDirectoryQuery.of("Lopez", courseId);
        when(studentDomain.searchDirectory(query, pageQuery)).thenReturn(emptyPage);

        PageResult<StudentDirectoryItem> result = studentService.search(query, pageQuery);

        assertThat(result).isSameAs(emptyPage);
    }

    /**
     * A caller that filtered by nothing is asking about the students still on the roll. Read as "no
     * filter" it would answer with the ones who left as well, and every existing picker in the app
     * would start offering students the school no longer has.
     */
    @Test
    void directoryQuery_withoutAScope_meansTheOnesStillOnTheRoll() {
        StudentDirectoryQuery query = new StudentDirectoryQuery(null, null, null, null, null, null);

        assertThat(query.scope()).isEqualTo(StudentDirectoryScope.ACTIVE);
    }

    /**
     * The directory speaks about one gestión. Without that rule a student who moved up appears once
     * per enrolment while the total counts them once, and the page disagrees with its own footer.
     */
    @Test
    void search_namingNoGestion_asksAboutTheCurrentOne() {
        when(academicYearDomain.currentYearId()).thenReturn(7);
        when(studentDomain.searchDirectory(any(StudentDirectoryQuery.class), eq(pageQuery)))
                .thenReturn(emptyPage);

        studentService.search(StudentDirectoryQuery.of(null, null), pageQuery);

        ArgumentCaptor<StudentDirectoryQuery> sent =
                ArgumentCaptor.forClass(StudentDirectoryQuery.class);
        verify(studentDomain).searchDirectory(sent.capture(), eq(pageQuery));
        assertThat(sent.getValue().academicYearId()).isEqualTo(7);
    }

    @Test
    void search_namingAGestion_keepsTheOneItWasGiven() {
        StudentDirectoryQuery query =
                new StudentDirectoryQuery(null, null, null, null, 3, StudentDirectoryScope.ALL);
        when(studentDomain.searchDirectory(query, pageQuery)).thenReturn(emptyPage);

        studentService.search(query, pageQuery);

        verify(studentDomain).searchDirectory(query, pageQuery);
        verify(academicYearDomain, never()).currentYearId();
    }

    /**
     * A course belongs to exactly one gestión already. Narrowing to the current year on top of it
     * would answer with nothing for every teacher whose course is not this year's.
     */
    @Test
    void search_pinnedToACourse_doesNotReachForTheCurrentGestion() {
        StudentDirectoryQuery query = StudentDirectoryQuery.of(null, UUID.randomUUID());
        when(studentDomain.searchDirectory(query, pageQuery)).thenReturn(emptyPage);

        studentService.search(query, pageQuery);

        verify(studentDomain).searchDirectory(query, pageQuery);
        verify(academicYearDomain, never()).currentYearId();
    }

    private static WithdrawStudentCommand withdrawal(
            UUID id, StudentWithdrawalReason reason, String note, UUID actor) {
        return new WithdrawStudentCommand(id, reason, note, actor);
    }

    @Test
    void withdraw_activeStudent_setsWithdrawnAndWithdrawsEnrollments() {
        UUID id = UUID.randomUUID();
        UUID director = UUID.randomUUID();
        Student student = Student.builder().id(id).status("Effective").build();
        when(studentDomain.findById(id)).thenReturn(Optional.of(student));

        studentService.withdraw(
                withdrawal(id, StudentWithdrawalReason.RETIRO_VOLUNTARIO, null, director));

        verify(studentDomain)
                .updateStatus(
                        id,
                        new StudentStatusChange("Withdrawn", "Retiro Voluntario", null, director));
        verify(courseEnrollmentDomain).withdrawActiveEnrollments(id);
    }

    /**
     * "Otro" is the category for a reason the list does not have, so it is the one that has to say
     * what that reason was. Stored alone it puts the word "Otro" in front of a teacher and nothing
     * else — the exact question the notice is supposed to answer.
     */
    @Test
    void withdraw_theOpenCategoryWithoutWords_isRefused() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                studentService.withdraw(
                                        withdrawal(
                                                id,
                                                StudentWithdrawalReason.OTRO,
                                                "   ",
                                                UUID.randomUUID())))
                .isInstanceOf(ValidationException.class);

        verify(studentDomain, never()).findById(any());
    }

    @Test
    void withdraw_theOpenCategoryWithWords_carriesThem() {
        UUID id = UUID.randomUUID();
        UUID director = UUID.randomUUID();
        Student student = Student.builder().id(id).status("Effective").build();
        when(studentDomain.findById(id)).thenReturn(Optional.of(student));

        studentService.withdraw(
                withdrawal(
                        id, StudentWithdrawalReason.OTRO, "  Se mudó a Santa Cruz.  ", director));

        // Trimmed: the surrounding blanks are typing, not part of what the Director said.
        verify(studentDomain)
                .updateStatus(
                        id,
                        new StudentStatusChange(
                                "Withdrawn", "Otro", "Se mudó a Santa Cruz.", director));
    }

    /** A named category may still be expanded on — "Transferencia" does not say to where. */
    @Test
    void withdraw_aNamedCategoryMayCarryWordsToo() {
        UUID id = UUID.randomUUID();
        UUID director = UUID.randomUUID();
        Student student = Student.builder().id(id).status("Effective").build();
        when(studentDomain.findById(id)).thenReturn(Optional.of(student));

        studentService.withdraw(
                withdrawal(
                        id,
                        StudentWithdrawalReason.TRANSFERENCIA,
                        "A la U.E. San Martín.",
                        director));

        verify(studentDomain)
                .updateStatus(
                        id,
                        new StudentStatusChange(
                                "Withdrawn", "Transferencia", "A la U.E. San Martín.", director));
    }

    /** Blank is not a note. Kept as null so the reader is not shown an empty line. */
    @Test
    void withdraw_aNamedCategoryWithBlankWords_recordsNoNote() {
        UUID id = UUID.randomUUID();
        UUID director = UUID.randomUUID();
        Student student = Student.builder().id(id).status("Effective").build();
        when(studentDomain.findById(id)).thenReturn(Optional.of(student));

        studentService.withdraw(
                withdrawal(id, StudentWithdrawalReason.RETIRO_VOLUNTARIO, "   ", director));

        verify(studentDomain)
                .updateStatus(
                        id,
                        new StudentStatusChange("Withdrawn", "Retiro Voluntario", null, director));
    }

    /**
     * The fact leaves here and nothing else. Who has to hear about it is a question about the
     * school — which teachers ran this student's courses — and it is not this service's to answer.
     */
    @Test
    void withdraw_written_statesWhatHappened() {
        UUID id = UUID.randomUUID();
        Student student =
                Student.builder()
                        .id(id)
                        .status("Effective")
                        .names("Ana")
                        .lastNames("Quispe")
                        .build();
        when(studentDomain.findById(id)).thenReturn(Optional.of(student));

        studentService.withdraw(
                withdrawal(
                        id,
                        StudentWithdrawalReason.OTRO,
                        "Se mudó a Santa Cruz.",
                        UUID.randomUUID()));

        verify(events)
                .publish(new StudentWithdrawn(id, "Ana Quispe", "Otro", "Se mudó a Santa Cruz."));
    }

    /** Nothing was written, so there is nothing to tell anybody about. */
    @Test
    void withdraw_refused_statesNothing() {
        UUID id = UUID.randomUUID();
        Student student = Student.builder().id(id).status("Withdrawn").build();
        when(studentDomain.findById(id)).thenReturn(Optional.of(student));

        assertThatThrownBy(
                        () ->
                                studentService.withdraw(
                                        withdrawal(
                                                id,
                                                StudentWithdrawalReason.TRANSFERENCIA,
                                                null,
                                                UUID.randomUUID())))
                .isInstanceOf(ConflictException.class);

        verify(events, never()).publish(any());
    }

    @Test
    void withdraw_alreadyWithdrawn_throwsConflict() {
        UUID id = UUID.randomUUID();
        Student student = Student.builder().id(id).status("Withdrawn").build();
        when(studentDomain.findById(id)).thenReturn(Optional.of(student));

        assertThatThrownBy(
                        () ->
                                studentService.withdraw(
                                        withdrawal(
                                                id,
                                                StudentWithdrawalReason.OTRO,
                                                "x",
                                                UUID.randomUUID())))
                .isInstanceOf(ConflictException.class);

        verify(studentDomain, never()).updateStatus(any(), any());
        verify(courseEnrollmentDomain, never()).withdrawActiveEnrollments(any());
    }

    @Test
    void withdraw_missingStudent_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(studentDomain.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                studentService.withdraw(
                                        withdrawal(
                                                id,
                                                StudentWithdrawalReason.TRANSFERENCIA,
                                                null,
                                                UUID.randomUUID())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void withdraw_nullReason_throwsValidationException() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                studentService.withdraw(
                                        withdrawal(id, null, null, UUID.randomUUID())))
                .isInstanceOf(ValidationException.class);

        verify(studentDomain, never()).findById(any());
    }
}
