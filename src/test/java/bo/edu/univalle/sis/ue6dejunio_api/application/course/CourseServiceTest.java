package bo.edu.univalle.sis.ue6dejunio_api.application.course;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.course.CourseService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.CreateCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock private ICourseDomain courseDomain;
    @InjectMocks private CourseService courseService;

    private Course course(UUID id) {
        return new Course(id, 1, "Primero", 1, "A", 1, 2026, null, null, true);
    }

    @Test
    void create_autoYear_success() {
        when(courseDomain.gradeExists(1)).thenReturn(true);
        when(courseDomain.parallelExists(1)).thenReturn(true);
        when(courseDomain.currentAcademicYearId()).thenReturn(1);
        when(courseDomain.existsByGradeParallelYear(1, 1, 1)).thenReturn(false);
        when(courseDomain.create(1, 1, 1, null)).thenReturn(course(UUID.randomUUID()));

        Course r = courseService.create(new CreateCourseCommand(1, 1, null));
        assertThat(r.active()).isTrue();
    }

    @Test
    void create_duplicate_throws() {
        when(courseDomain.gradeExists(1)).thenReturn(true);
        when(courseDomain.parallelExists(1)).thenReturn(true);
        when(courseDomain.currentAcademicYearId()).thenReturn(1);
        when(courseDomain.existsByGradeParallelYear(1, 1, 1)).thenReturn(true);
        assertThatThrownBy(() -> courseService.create(new CreateCourseCommand(1, 1, null)))
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
        assertThatThrownBy(() -> courseService.create(new CreateCourseCommand(1, 1, t)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_gradeMissing_throws() {
        when(courseDomain.gradeExists(1)).thenReturn(false);
        assertThatThrownBy(() -> courseService.create(new CreateCourseCommand(1, 1, null)))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void setHomeroom_technical_throws() {
        UUID id = UUID.randomUUID();
        UUID t = UUID.randomUUID();
        when(courseDomain.findById(id)).thenReturn(Optional.of(course(id)));
        when(courseDomain.userIsNonTechnicalTeacher(t)).thenReturn(false);
        assertThatThrownBy(() -> courseService.setHomeroomTeacher(id, t))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
