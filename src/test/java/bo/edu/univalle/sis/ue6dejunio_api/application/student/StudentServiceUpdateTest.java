package bo.edu.univalle.sis.ue6dejunio_api.application.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.student.StudentService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDetails;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentDomain;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Completing a student by hand. The RUDE and CI each name one student, so a code someone else
 * already holds is refused rather than written.
 */
@ExtendWith(MockitoExtension.class)
class StudentServiceUpdateTest {

    @Mock private IStudentDomain studentDomain;
    @InjectMocks private StudentService studentService;

    private final UUID id = UUID.randomUUID();
    private final StudentDetails details =
            new StudentDetails("R1", "C1", "Ana", "Quispe", LocalDate.of(2015, 1, 1), "F");

    private static Student student(UUID id, String rude, String card) {
        return Student.builder()
                .id(id)
                .rudeCode(rude)
                .identityCard(card)
                .names("Ana")
                .lastNames("Quispe")
                .status("Effective")
                .build();
    }

    @Test
    void writesTheDetails_andAnswersWithTheStudentAsStored() {
        Student stored = student(id, "R1", "C1");
        when(studentDomain.findById(id))
                .thenReturn(Optional.of(student(id, null, null)), Optional.of(stored));
        when(studentDomain.findByRudeCodeIn(Set.of("R1"))).thenReturn(List.of());
        when(studentDomain.findByIdentityCardIn(Set.of("C1"))).thenReturn(List.of());

        Student result = studentService.update(id, details);

        verify(studentDomain).updateDetails(id, details);
        assertThat(result).isSameAs(stored);
    }

    @Test
    void keepingItsOwnCodes_isNoCollision() {
        when(studentDomain.findById(id)).thenReturn(Optional.of(student(id, "R1", "C1")));
        when(studentDomain.findByRudeCodeIn(Set.of("R1")))
                .thenReturn(List.of(student(id, "R1", "C1")));
        when(studentDomain.findByIdentityCardIn(Set.of("C1")))
                .thenReturn(List.of(student(id, "R1", "C1")));

        studentService.update(id, details);

        verify(studentDomain).updateDetails(id, details);
    }

    @Test
    void aRudeAnotherStudentHolds_isAConflict() {
        when(studentDomain.findById(id)).thenReturn(Optional.of(student(id, null, null)));
        when(studentDomain.findByRudeCodeIn(Set.of("R1")))
                .thenReturn(List.of(student(UUID.randomUUID(), "R1", "X")));

        assertThatThrownBy(() -> studentService.update(id, details))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("R1");
        verify(studentDomain, never()).updateDetails(any(), any());
    }

    @Test
    void aCardAnotherStudentHolds_isAConflict() {
        when(studentDomain.findById(id)).thenReturn(Optional.of(student(id, null, null)));
        when(studentDomain.findByRudeCodeIn(Set.of("R1"))).thenReturn(List.of());
        when(studentDomain.findByIdentityCardIn(Set.of("C1")))
                .thenReturn(List.of(student(UUID.randomUUID(), "X", "C1")));

        assertThatThrownBy(() -> studentService.update(id, details))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("C1");
        verify(studentDomain, never()).updateDetails(any(), any());
    }

    @Test
    void absentCodes_areNotLookedUp() {
        when(studentDomain.findById(id)).thenReturn(Optional.of(student(id, null, null)));
        StudentDetails nameOnly = new StudentDetails(null, null, "Ana", "Quispe", null, null);

        studentService.update(id, nameOnly);

        verify(studentDomain, never()).findByRudeCodeIn(any());
        verify(studentDomain, never()).findByIdentityCardIn(any());
        verify(studentDomain).updateDetails(id, nameOnly);
    }

    @Test
    void anUnknownStudent_isNotFound() {
        when(studentDomain.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentService.update(id, details))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(studentDomain, never()).updateDetails(any(), any());
    }
}
