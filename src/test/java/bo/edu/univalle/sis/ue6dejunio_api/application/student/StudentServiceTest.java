package bo.edu.univalle.sis.ue6dejunio_api.application.student;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.student.StudentService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentWithdrawalReason;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
    @InjectMocks private StudentService studentService;

    @Test
    void search_delegatesToDomain() {
        UUID courseId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 30);
        Page<StudentDirectoryItem> expected = new PageImpl<>(List.of());
        when(studentDomain.searchDirectory("Lopez", courseId, pageable)).thenReturn(expected);

        Page<StudentDirectoryItem> result = studentService.search("Lopez", courseId, pageable);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void withdraw_activeStudent_setsWithdrawnAndWithdrawsEnrollments() {
        UUID id = UUID.randomUUID();
        Student student = Student.builder().id(id).status("Effective").build();
        when(studentDomain.findById(id)).thenReturn(Optional.of(student));

        studentService.withdraw(id, StudentWithdrawalReason.RETIRO_VOLUNTARIO);

        verify(studentDomain).updateStatus(id, "Withdrawn", "Retiro Voluntario");
        verify(courseEnrollmentDomain).withdrawActiveEnrollments(id);
    }

    @Test
    void withdraw_alreadyWithdrawn_throwsConflict() {
        UUID id = UUID.randomUUID();
        Student student = Student.builder().id(id).status("Withdrawn").build();
        when(studentDomain.findById(id)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> studentService.withdraw(id, StudentWithdrawalReason.OTRO))
            .isInstanceOf(ConflictException.class);

        verify(studentDomain, never()).updateStatus(any(), any(), any());
        verify(courseEnrollmentDomain, never()).withdrawActiveEnrollments(any());
    }

    @Test
    void withdraw_missingStudent_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(studentDomain.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentService.withdraw(id, StudentWithdrawalReason.TRANSFERENCIA))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void withdraw_nullReason_throwsValidationException() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> studentService.withdraw(id, null))
            .isInstanceOf(bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException.class);

        verify(studentDomain, never()).findById(any());
    }
}
