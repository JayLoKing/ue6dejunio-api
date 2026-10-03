package bo.edu.univalle.sis.ue6dejunio_api.application.course;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * A course that holds students or academic content cannot be taken out of service, by DELETE or by
 * a PUT that flips {@code active} to false. Bare class groups do not count.
 */
@ExtendWith(MockitoExtension.class)
class CourseServiceInUseTest {

    @Mock private ICourseDomain courseDomain;
    @Mock private IAcademicYearDomain academicYearDomain;
    @Mock private IClassGroupService classGroupService;
    @Mock private IClassGroupDomain classGroupDomain;
    @Mock private ClosedYearGuard closedYearGuard;

    private final UUID id = UUID.randomUUID();

    private CourseService service() {
        return new CourseService(
                courseDomain,
                academicYearDomain,
                classGroupService,
                classGroupDomain,
                closedYearGuard);
    }

    private Course course(boolean active) {
        return new Course(id, 1, "Primero", 1, "A", 1, 2026, null, null, active, true);
    }

    private void existing(boolean active) {
        when(courseDomain.findById(id)).thenReturn(Optional.of(course(active)));
    }

    @Test
    void delete_withAnEnrollment_isRefusedAndStaysActive() {
        existing(true);
        when(courseDomain.hasEnrollments(id)).thenReturn(true);

        assertThatThrownBy(() -> service().delete(id))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("estudiantes inscritos");

        verify(courseDomain, never()).setActive(any(), any(Boolean.class));
    }

    @Test
    void delete_withAcademicContent_isRefusedAndStaysActive() {
        existing(true);
        when(courseDomain.hasEnrollments(id)).thenReturn(false);
        when(courseDomain.hasAcademicRecords(id)).thenReturn(true);

        assertThatThrownBy(() -> service().delete(id))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("registros académicos");

        verify(courseDomain, never()).setActive(any(), any(Boolean.class));
    }

    @Test
    void delete_ofAnUnusedCourse_deactivatesItAfterTheGuards() {
        existing(true);
        when(courseDomain.hasEnrollments(id)).thenReturn(false);
        when(courseDomain.hasAcademicRecords(id)).thenReturn(false);

        service().delete(id);

        InOrder order = inOrder(closedYearGuard, courseDomain);
        order.verify(closedYearGuard).requireOpenForCourse(id);
        order.verify(courseDomain).hasEnrollments(id);
        order.verify(courseDomain).setActive(id, false);
    }

    @Test
    void update_deactivatingACourseWithStudents_isRefused() {
        existing(true);
        when(courseDomain.hasEnrollments(id)).thenReturn(true);

        assertThatThrownBy(() -> service().update(id, new UpdateCourseCommand(null, false)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("estudiantes inscritos");

        verify(courseDomain, never()).setActive(any(), any(Boolean.class));
    }

    @Test
    void update_deactivatingAnUnusedCourse_goesThrough() {
        existing(true);
        when(courseDomain.hasEnrollments(id)).thenReturn(false);
        when(courseDomain.hasAcademicRecords(id)).thenReturn(false);
        when(courseDomain.setActive(id, false)).thenReturn(course(false));

        service().update(id, new UpdateCourseCommand(null, false));

        verify(courseDomain).setActive(id, false);
    }

    @Test
    void update_reactivatingACourseWithStudents_isNotChecked() {
        existing(false);
        when(courseDomain.setActive(id, true)).thenReturn(course(true));

        service().update(id, new UpdateCourseCommand(null, true));

        verify(courseDomain, never()).hasEnrollments(any());
        verify(courseDomain, never()).hasAcademicRecords(any());
        verify(courseDomain).setActive(id, true);
    }

    @Test
    void update_notTouchingActive_isNotChecked() {
        existing(true);

        service().update(id, new UpdateCourseCommand(null, null));

        verify(courseDomain, never()).hasEnrollments(any());
    }

    @Test
    void delete_inAClosedYear_isRefusedBeforeTheInUseCheck() {
        existing(true);
        doThrow(new ConflictException("La gestión 2026 está cerrada"))
                .when(closedYearGuard)
                .requireOpenForCourse(id);

        assertThatThrownBy(() -> service().delete(id)).hasMessageContaining("cerrada");

        verify(courseDomain, never()).hasEnrollments(any());
    }
}
