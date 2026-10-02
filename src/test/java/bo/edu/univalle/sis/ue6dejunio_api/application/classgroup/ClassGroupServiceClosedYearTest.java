package bo.edu.univalle.sis.ue6dejunio_api.application.classgroup;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.classgroup.ClassGroupService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.CreateClassGroupCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClassGroupServiceClosedYearTest {

    @Mock private IClassGroupDomain classGroupDomain;
    @Mock private ClosedYearGuard closedYearGuard;

    private final UUID courseId = UUID.randomUUID();
    private final UUID classGroupId = UUID.randomUUID();

    private ClassGroupService service() {
        return new ClassGroupService(classGroupDomain, closedYearGuard);
    }

    private static ConflictException closed() {
        return new ConflictException("La gestión 2026 está cerrada; no admite cambios.");
    }

    private ClassGroup classGroup() {
        return new ClassGroup(
                classGroupId,
                courseId,
                "Primero",
                "A",
                UUID.randomUUID(),
                "Lenguaje",
                UUID.randomUUID(),
                "Docente",
                true);
    }

    @Test
    void createForCourse_inAClosedYear_isRefusedBeforeAnyGroupIsCreated() {
        when(classGroupDomain.courseExists(courseId)).thenReturn(true);
        doThrow(closed()).when(closedYearGuard).requireOpenForCourse(courseId);
        CreateClassGroupCommand command =
                new CreateClassGroupCommand(
                        courseId,
                        List.of(
                                new CreateClassGroupCommand.Assignment(
                                        UUID.randomUUID(), UUID.randomUUID())));

        assertThatThrownBy(() -> service().createForCourse(command))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cerrada");

        verify(classGroupDomain, never()).create(any(), any(), any());
    }

    @Test
    void delete_inAClosedYear_isRefusedAndDeactivatesNothing() {
        when(classGroupDomain.findById(classGroupId)).thenReturn(Optional.of(classGroup()));
        doThrow(closed()).when(closedYearGuard).requireOpenForClassGroup(classGroupId);

        assertThatThrownBy(() -> service().delete(classGroupId))
                .isInstanceOf(ConflictException.class);

        verify(classGroupDomain, never()).setActive(any(), any(Boolean.class));
    }

    @Test
    void reassignTeacher_inAClosedYear_isRefusedAndChangesNothing() {
        when(classGroupDomain.findById(classGroupId)).thenReturn(Optional.of(classGroup()));
        doThrow(closed()).when(closedYearGuard).requireOpenForClassGroup(classGroupId);

        assertThatThrownBy(
                        () -> service().reassignTeacher(courseId, classGroupId, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);

        verify(classGroupDomain, never()).setTeacher(any(), any());
    }
}
