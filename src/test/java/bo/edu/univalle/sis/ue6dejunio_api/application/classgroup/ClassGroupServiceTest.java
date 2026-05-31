package bo.edu.univalle.sis.ue6dejunio_api.application.classgroup;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.classgroup.ClassGroupService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.CreateClassGroupCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassGroupServiceTest {

    @Mock private IClassGroupDomain classGroupDomain;
    @InjectMocks private ClassGroupService classGroupService;

    private final UUID subj1 = UUID.randomUUID();
    private final UUID subj2 = UUID.randomUUID();
    private final UUID teacher = UUID.randomUUID();

    private CreateClassGroupCommand cmd() {
        return new CreateClassGroupCommand(1, 1, List.of(
            new CreateClassGroupCommand.Assignment(subj1, teacher),
            new CreateClassGroupCommand.Assignment(subj2, teacher)
        ));
    }

    @Test
    void createCourse_createsNRows() {
        when(classGroupDomain.currentAcademicYearId()).thenReturn(1);
        when(classGroupDomain.existsAssignment(any(), eq(1), eq(1), eq(1))).thenReturn(false);
        when(classGroupDomain.createAssignment(eq(1), eq(1), eq(1), any(), eq(teacher)))
            .thenReturn(mockGroup());

        List<ClassGroup> result = classGroupService.createCourse(cmd());

        assertThat(result).hasSize(2);
        verify(classGroupDomain, times(2)).createAssignment(eq(1), eq(1), eq(1), any(), eq(teacher));
    }

    @Test
    void createCourse_duplicateAssignment_throws() {
        when(classGroupDomain.currentAcademicYearId()).thenReturn(1);
        when(classGroupDomain.existsAssignment(eq(subj1), eq(1), eq(1), eq(1))).thenReturn(true);
        assertThatThrownBy(() -> classGroupService.createCourse(cmd()))
            .isInstanceOf(DuplicateResourceException.class);
    }

    private ClassGroup mockGroup() {
        return new ClassGroup(UUID.randomUUID(), subj1, "Mate", teacher, "Doc",
            1, "1ro", 1, "A", 1, 2026);
    }
}
