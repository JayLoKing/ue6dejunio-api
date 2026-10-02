package bo.edu.univalle.sis.ue6dejunio_api.application.course;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.course.CourseService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.UpdateCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseDomain;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Changing a course of a closed year is refused before the course or its class groups move. */
@ExtendWith(MockitoExtension.class)
class CourseServiceClosedYearTest {

    @Mock private ICourseDomain courseDomain;
    @Mock private IAcademicYearDomain academicYearDomain;
    @Mock private IClassGroupService classGroupService;
    @Mock private IClassGroupDomain classGroupDomain;
    @Mock private ClosedYearGuard closedYearGuard;

    private final UUID id = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final UUID teacher = UUID.randomUUID();

    private CourseService service() {
        return new CourseService(
                courseDomain,
                academicYearDomain,
                classGroupService,
                classGroupDomain,
                closedYearGuard);
    }

    private static ConflictException closed() {
        return new ConflictException("La gestión 2026 está cerrada; no admite cambios.");
    }

    private Course course(UUID courseId) {
        return new Course(courseId, 1, "Primero", 1, "A", 1, 2026, teacher, "Nora", true, true);
    }

    @Test
    void update_inAClosedYear_isRefusedAndChangesNothing() {
        when(courseDomain.findById(id)).thenReturn(Optional.of(course(id)));
        doThrow(closed()).when(closedYearGuard).requireOpenForCourse(id);

        assertThatThrownBy(() -> service().update(id, new UpdateCourseCommand(null, false)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cerrada");

        verify(courseDomain, never()).setActive(any(), any(Boolean.class));
        verify(courseDomain, never()).setHomeroomTeacher(any(), any());
    }

    @Test
    void setHomeroomTeacher_inAClosedYear_isRefusedBeforeAnyLookupOrMove() {
        doThrow(closed()).when(closedYearGuard).requireOpenForCourse(id);

        assertThatThrownBy(() -> service().setHomeroomTeacher(id, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);

        verify(courseDomain, never()).setHomeroomTeacher(any(), any());
        verify(classGroupDomain, never()).reassignTeacherInCourse(any(), any(), any());
    }

    @Test
    void swap_whenEitherCourseIsClosed_isRefusedAskingAboutBothAtOnce() {
        when(courseDomain.findById(id)).thenReturn(Optional.of(course(id)));
        when(courseDomain.findById(other)).thenReturn(Optional.of(course(other)));
        doThrow(closed()).when(closedYearGuard).requireOpenForCourses(List.of(id, other));

        assertThatThrownBy(() -> service().swapHomeroomTeachers(id, other))
                .isInstanceOf(ConflictException.class);

        verify(courseDomain, never()).setHomeroomTeacher(any(), any());
        verify(classGroupDomain, never()).reassignTeacherInCourse(any(), any(), any());
    }

    @Test
    void delete_inAClosedYear_isRefused() {
        when(courseDomain.findById(id)).thenReturn(Optional.of(course(id)));
        doThrow(closed()).when(closedYearGuard).requireOpenForCourse(id);

        assertThatThrownBy(() -> service().delete(id)).isInstanceOf(ConflictException.class);

        verify(courseDomain, never()).setActive(any(), any(Boolean.class));
    }
}
