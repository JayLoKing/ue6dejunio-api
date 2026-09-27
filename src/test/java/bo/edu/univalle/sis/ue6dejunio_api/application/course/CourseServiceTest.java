package bo.edu.univalle.sis.ue6dejunio_api.application.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.course.CourseService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.CourseWithSubjects;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.CreateCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.UpdateCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseDomain;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock private ICourseDomain courseDomain;
    @Mock private IClassGroupService classGroupService;
    @InjectMocks private CourseService courseService;

    private Course course(UUID id) {
        return new Course(id, 1, "Primero", 1, "A", 1, 2026, null, null, true, false);
    }

    private Course courseWithHomeroom(UUID id, UUID teacherId, boolean teacherActive) {
        return new Course(
                id, 1, "Primero", 1, "A", 1, 2026, teacherId, "Nora Arnez", true, teacherActive);
    }

    private CreateCourseCommand cmd(UUID homeroom) {
        return new CreateCourseCommand(
                1,
                1,
                homeroom,
                List.of(new CreateCourseCommand.Assignment(UUID.randomUUID(), UUID.randomUUID())));
    }

    @Test
    void create_autoYear_createsCourseAndClassGroups() {
        UUID id = UUID.randomUUID();
        when(courseDomain.gradeExists(1)).thenReturn(true);
        when(courseDomain.parallelExists(1)).thenReturn(true);
        when(courseDomain.currentAcademicYearId()).thenReturn(1);
        when(courseDomain.existsByGradeParallelYear(1, 1, 1)).thenReturn(false);
        when(courseDomain.create(1, 1, 1, null)).thenReturn(course(id));
        when(classGroupService.createForCourse(any())).thenReturn(List.of());

        CourseWithSubjects r = courseService.create(cmd(null));
        assertThat(r.course().id()).isEqualTo(id);
    }

    @Test
    void create_duplicate_throws() {
        when(courseDomain.gradeExists(1)).thenReturn(true);
        when(courseDomain.parallelExists(1)).thenReturn(true);
        when(courseDomain.currentAcademicYearId()).thenReturn(1);
        when(courseDomain.existsByGradeParallelYear(1, 1, 1)).thenReturn(true);
        assertThatThrownBy(() -> courseService.create(cmd(null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void create_homeroomTechnical_throws() {
        UUID t = UUID.randomUUID();
        when(courseDomain.gradeExists(1)).thenReturn(true);
        when(courseDomain.parallelExists(1)).thenReturn(true);
        when(courseDomain.currentAcademicYearId()).thenReturn(1);
        when(courseDomain.existsByGradeParallelYear(1, 1, 1)).thenReturn(false);
        when(courseDomain.userIsNonTechnicalTeacher(t)).thenReturn(false);
        assertThatThrownBy(() -> courseService.create(cmd(t)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_gradeMissing_throws() {
        when(courseDomain.gradeExists(1)).thenReturn(false);
        assertThatThrownBy(() -> courseService.create(cmd(null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void setHomeroom_technical_throws() {
        UUID id = UUID.randomUUID();
        UUID t = UUID.randomUUID();
        when(courseDomain.findById(id)).thenReturn(Optional.of(course(id)));
        when(courseDomain.userIsNonTechnicalTeacher(t)).thenReturn(false);
        assertThatThrownBy(() -> courseService.setHomeroomTeacher(id, t))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void setHomeroom_courseHasNoHomeroomTeacher_assignmentAllowed() {
        UUID id = UUID.randomUUID();
        UUID newTeacher = UUID.randomUUID();
        when(courseDomain.findById(id)).thenReturn(Optional.of(course(id)));
        when(courseDomain.userIsNonTechnicalTeacher(newTeacher)).thenReturn(true);
        when(courseDomain.setHomeroomTeacher(id, newTeacher))
                .thenReturn(courseWithHomeroom(id, newTeacher, true));

        Course result = courseService.setHomeroomTeacher(id, newTeacher);

        assertThat(result.homeroomTeacherId()).isEqualTo(newTeacher);
    }

    @Test
    void setHomeroom_outgoingTeacherAlreadyInactive_reassignmentAllowed() {
        UUID id = UUID.randomUUID();
        UUID outgoing = UUID.randomUUID();
        UUID incoming = UUID.randomUUID();
        when(courseDomain.findById(id))
                .thenReturn(Optional.of(courseWithHomeroom(id, outgoing, false)));
        when(courseDomain.userIsNonTechnicalTeacher(incoming)).thenReturn(true);
        when(courseDomain.setHomeroomTeacher(id, incoming))
                .thenReturn(courseWithHomeroom(id, incoming, true));

        Course result = courseService.setHomeroomTeacher(id, incoming);

        assertThat(result.homeroomTeacherId()).isEqualTo(incoming);
    }

    @Test
    void setHomeroom_outgoingTeacherActiveButSamePerson_allowedAsNoOp() {
        UUID id = UUID.randomUUID();
        UUID teacher = UUID.randomUUID();
        when(courseDomain.findById(id))
                .thenReturn(Optional.of(courseWithHomeroom(id, teacher, true)));
        when(courseDomain.userIsNonTechnicalTeacher(teacher)).thenReturn(true);
        when(courseDomain.setHomeroomTeacher(id, teacher))
                .thenReturn(courseWithHomeroom(id, teacher, true));

        Course result = courseService.setHomeroomTeacher(id, teacher);

        assertThat(result.homeroomTeacherId()).isEqualTo(teacher);
    }

    @Test
    void setHomeroom_outgoingTeacherActiveAndDifferentPerson_throwsAndNeverReassigns() {
        UUID id = UUID.randomUUID();
        UUID outgoing = UUID.randomUUID();
        UUID incoming = UUID.randomUUID();
        when(courseDomain.findById(id))
                .thenReturn(Optional.of(courseWithHomeroom(id, outgoing, true)));
        when(courseDomain.userIsNonTechnicalTeacher(incoming)).thenReturn(true);

        assertThatThrownBy(() -> courseService.setHomeroomTeacher(id, incoming))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Nora Arnez");

        verify(courseDomain, never()).setHomeroomTeacher(any(), any());
    }

    @Test
    void update_outgoingTeacherActiveAndDifferentPerson_throwsAndNeverReassigns() {
        UUID id = UUID.randomUUID();
        UUID outgoing = UUID.randomUUID();
        UUID incoming = UUID.randomUUID();
        when(courseDomain.findById(id))
                .thenReturn(Optional.of(courseWithHomeroom(id, outgoing, true)));
        when(courseDomain.userIsNonTechnicalTeacher(incoming)).thenReturn(true);

        assertThatThrownBy(() -> courseService.update(id, new UpdateCourseCommand(incoming, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Nora Arnez");

        verify(courseDomain, never()).setHomeroomTeacher(any(), any());
    }

    /**
     * The whole-school reports read every course of a gestión, and a report built from the first
     * page only drops a classroom without saying so.
     */
    @Test
    void allOfYear_readsEveryPageAndNotJustTheFirst() {
        Course onFirstPage = course(UUID.randomUUID());
        Course onSecondPage = course(UUID.randomUUID());
        when(courseDomain.list(eq(7), any(PageQuery.class)))
                .thenReturn(new PageResult<>(List.of(onFirstPage), 0, 200, 2L))
                .thenReturn(new PageResult<>(List.of(onSecondPage), 1, 200, 2L));

        assertThat(courseService.allOfYear(7)).containsExactly(onFirstPage, onSecondPage);
    }

    /**
     * The second exit from the loop. A store that keeps answering nothing against an inflated total
     * would otherwise be read forever.
     */
    @Test
    void allOfYear_stopsWhenAPageComesBackEmpty() {
        when(courseDomain.list(eq(7), any(PageQuery.class)))
                .thenReturn(new PageResult<>(List.of(), 0, 200, 99L));

        assertThat(courseService.allOfYear(7)).isEmpty();
    }
}
