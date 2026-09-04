package bo.edu.univalle.sis.ue6dejunio_api.application.student;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.student.StudentService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryScope;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentStatusChange;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentWithdrawalReason;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentWithdrawn;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.WithdrawStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.event.IDomainEventPublisher;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock private IStudentDomain studentDomain;
    @Mock private ICourseEnrollmentDomain courseEnrollmentDomain;
    @Mock private IDomainEventPublisher events;
    @InjectMocks private StudentService studentService;

    @Test
    void search_delegatesToDomain() {
        UUID courseId = UUID.randomUUID();
        PageQuery pageQuery = PageQuery.of(0, 30);
        StudentDirectoryQuery query = StudentDirectoryQuery.of("Lopez", courseId);
        PageResult<StudentDirectoryItem> expected = new PageResult<>(List.of(), 0, 30, 0);
        when(studentDomain.searchDirectory(query, pageQuery)).thenReturn(expected);

        PageResult<StudentDirectoryItem> result = studentService.search(query, pageQuery);

        assertThat(result).isSameAs(expected);
    }

    /**
     * A caller that filtered by nothing is asking about the students still on the roll. Read as
     * "no filter" it would answer with the ones who left as well, and every existing picker in the
     * app would start offering students the school no longer has.
     */
    @Test
    void directoryQuery_withoutAScope_meansTheOnesStillOnTheRoll() {
        StudentDirectoryQuery query = new StudentDirectoryQuery(null, null, null, null, null);

        assertThat(query.scope()).isEqualTo(StudentDirectoryScope.ACTIVE);
    }

    private static WithdrawStudentCommand withdrawal(UUID id, StudentWithdrawalReason reason,
                                                     String note, UUID actor) {
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

        verify(studentDomain).updateStatus(id, new StudentStatusChange(
            "Withdrawn", "Retiro Voluntario", null, director));
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

        assertThatThrownBy(() -> studentService.withdraw(
            withdrawal(id, StudentWithdrawalReason.OTRO, "   ", UUID.randomUUID())))
            .isInstanceOf(ValidationException.class);

        verify(studentDomain, never()).findById(any());
    }

    @Test
    void withdraw_theOpenCategoryWithWords_carriesThem() {
        UUID id = UUID.randomUUID();
        UUID director = UUID.randomUUID();
        Student student = Student.builder().id(id).status("Effective").build();
        when(studentDomain.findById(id)).thenReturn(Optional.of(student));

        studentService.withdraw(withdrawal(
            id, StudentWithdrawalReason.OTRO, "  Se mudó a Santa Cruz.  ", director));

        // Trimmed: the surrounding blanks are typing, not part of what the Director said.
        verify(studentDomain).updateStatus(id, new StudentStatusChange(
            "Withdrawn", "Otro", "Se mudó a Santa Cruz.", director));
    }

    /** A named category may still be expanded on — "Transferencia" does not say to where. */
    @Test
    void withdraw_aNamedCategoryMayCarryWordsToo() {
        UUID id = UUID.randomUUID();
        UUID director = UUID.randomUUID();
        Student student = Student.builder().id(id).status("Effective").build();
        when(studentDomain.findById(id)).thenReturn(Optional.of(student));

        studentService.withdraw(withdrawal(
            id, StudentWithdrawalReason.TRANSFERENCIA, "A la U.E. San Martín.", director));

        verify(studentDomain).updateStatus(id, new StudentStatusChange(
            "Withdrawn", "Transferencia", "A la U.E. San Martín.", director));
    }

    /** Blank is not a note. Kept as null so the reader is not shown an empty line. */
    @Test
    void withdraw_aNamedCategoryWithBlankWords_recordsNoNote() {
        UUID id = UUID.randomUUID();
        UUID director = UUID.randomUUID();
        Student student = Student.builder().id(id).status("Effective").build();
        when(studentDomain.findById(id)).thenReturn(Optional.of(student));

        studentService.withdraw(withdrawal(
            id, StudentWithdrawalReason.RETIRO_VOLUNTARIO, "   ", director));

        verify(studentDomain).updateStatus(id, new StudentStatusChange(
            "Withdrawn", "Retiro Voluntario", null, director));
    }

    /**
     * The fact leaves here and nothing else. Who has to hear about it is a question about the
     * school — which teachers ran this student's courses — and it is not this service's to answer.
     */
    @Test
    void withdraw_written_statesWhatHappened() {
        UUID id = UUID.randomUUID();
        Student student = Student.builder()
            .id(id).status("Effective").names("Ana").lastNames("Quispe").build();
        when(studentDomain.findById(id)).thenReturn(Optional.of(student));

        studentService.withdraw(withdrawal(
            id, StudentWithdrawalReason.OTRO, "Se mudó a Santa Cruz.", UUID.randomUUID()));

        verify(events).publish(new StudentWithdrawn(
            id, "Ana Quispe", "Otro", "Se mudó a Santa Cruz."));
    }

    /** Nothing was written, so there is nothing to tell anybody about. */
    @Test
    void withdraw_refused_statesNothing() {
        UUID id = UUID.randomUUID();
        Student student = Student.builder().id(id).status("Withdrawn").build();
        when(studentDomain.findById(id)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> studentService.withdraw(
            withdrawal(id, StudentWithdrawalReason.TRANSFERENCIA, null, UUID.randomUUID())))
            .isInstanceOf(ConflictException.class);

        verify(events, never()).publish(any());
    }

    @Test
    void withdraw_alreadyWithdrawn_throwsConflict() {
        UUID id = UUID.randomUUID();
        Student student = Student.builder().id(id).status("Withdrawn").build();
        when(studentDomain.findById(id)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> studentService.withdraw(
            withdrawal(id, StudentWithdrawalReason.OTRO, "x", UUID.randomUUID())))
            .isInstanceOf(ConflictException.class);

        verify(studentDomain, never()).updateStatus(any(), any());
        verify(courseEnrollmentDomain, never()).withdrawActiveEnrollments(any());
    }

    @Test
    void withdraw_missingStudent_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(studentDomain.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentService.withdraw(
            withdrawal(id, StudentWithdrawalReason.TRANSFERENCIA, null, UUID.randomUUID())))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void withdraw_nullReason_throwsValidationException() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> studentService.withdraw(
            withdrawal(id, null, null, UUID.randomUUID())))
            .isInstanceOf(ValidationException.class);

        verify(studentDomain, never()).findById(any());
    }
}
