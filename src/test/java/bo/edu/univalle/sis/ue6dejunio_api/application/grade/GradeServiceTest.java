package bo.edu.univalle.sis.ue6dejunio_api.application.grade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.grade.GradeService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.grade.CreateGradeCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.grade.Grade;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.grade.UpdateGradeCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.grade.IGradeDomain;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GradeServiceTest {

    @Mock private IGradeDomain gradeDomain;
    @InjectMocks private GradeService gradeService;

    private CreateGradeCommand cmd() {
        return new CreateGradeCommand("1ro", 1);
    }

    @Test
    void create_success() {
        when(gradeDomain.countByLevel(1)).thenReturn(5L);
        when(gradeDomain.levelExists(1)).thenReturn(true);
        when(gradeDomain.existsByNameAndLevel("1ro", 1)).thenReturn(false);
        when(gradeDomain.create("1ro", 1)).thenReturn(new Grade(7, "1ro", 1, "Primaria"));

        Grade result = gradeService.create(cmd());

        assertThat(result.name()).isEqualTo("1ro");
    }

    @Test
    void create_sixthLimitReached_throws() {
        when(gradeDomain.countByLevel(1)).thenReturn(6L);
        assertThatThrownBy(() -> gradeService.create(cmd()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("6");
    }

    @Test
    void create_fullLevelDoesNotBlockAnotherLevel() {
        // Level 1 holds six grades; the grade being created belongs to level 2.
        when(gradeDomain.countByLevel(2)).thenReturn(0L);
        when(gradeDomain.levelExists(2)).thenReturn(true);
        when(gradeDomain.existsByNameAndLevel("1ro", 2)).thenReturn(false);
        when(gradeDomain.create("1ro", 2)).thenReturn(new Grade(8, "1ro", 2, "Secundaria"));

        Grade result = gradeService.create(new CreateGradeCommand("1ro", 2));

        assertThat(result.levelId()).isEqualTo(2);
    }

    @Test
    void create_limitMessageSaysPerLevel() {
        when(gradeDomain.countByLevel(1)).thenReturn(6L);
        assertThatThrownBy(() -> gradeService.create(cmd()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("por nivel");
    }

    @Test
    void update_movingToAFullLevel_throws() {
        when(gradeDomain.findById(3)).thenReturn(Optional.of(new Grade(3, "3ro", 1, "Primaria")));
        when(gradeDomain.levelExists(2)).thenReturn(true);
        when(gradeDomain.countByLevel(2)).thenReturn(6L);
        assertThatThrownBy(() -> gradeService.update(3, new UpdateGradeCommand("3ro", 2)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("6");
    }

    @Test
    void update_movingToALevelWithRoom_succeeds() {
        when(gradeDomain.findById(3)).thenReturn(Optional.of(new Grade(3, "3ro", 1, "Primaria")));
        when(gradeDomain.levelExists(2)).thenReturn(true);
        when(gradeDomain.countByLevel(2)).thenReturn(5L);
        when(gradeDomain.existsByNameAndLevel("3ro", 2)).thenReturn(false);
        when(gradeDomain.update(3, "3ro", 2)).thenReturn(new Grade(3, "3ro", 2, "Secundaria"));

        assertThat(gradeService.update(3, new UpdateGradeCommand("3ro", 2)).levelId()).isEqualTo(2);
    }

    @Test
    void update_inTheSameFullLevel_isNotBlocked() {
        // Renaming inside a level that already holds six does not add a grade to it.
        when(gradeDomain.findById(3)).thenReturn(Optional.of(new Grade(3, "3ro", 1, "Primaria")));
        when(gradeDomain.levelExists(1)).thenReturn(true);
        when(gradeDomain.existsByNameAndLevel("Tercero", 1)).thenReturn(false);
        when(gradeDomain.update(3, "Tercero", 1))
                .thenReturn(new Grade(3, "Tercero", 1, "Primaria"));

        assertThat(gradeService.update(3, new UpdateGradeCommand("Tercero", 1)).name())
                .isEqualTo("Tercero");
        verify(gradeDomain, never()).countByLevel(1);
    }

    @Test
    void create_levelNotFound_throws() {
        when(gradeDomain.countByLevel(1)).thenReturn(0L);
        when(gradeDomain.levelExists(1)).thenReturn(false);
        assertThatThrownBy(() -> gradeService.create(cmd()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_duplicateNameInLevel_throws() {
        when(gradeDomain.countByLevel(1)).thenReturn(0L);
        when(gradeDomain.levelExists(1)).thenReturn(true);
        when(gradeDomain.existsByNameAndLevel("1ro", 1)).thenReturn(true);
        assertThatThrownBy(() -> gradeService.create(cmd()))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void delete_withCourses_throws() {
        when(gradeDomain.findById(3)).thenReturn(Optional.of(new Grade(3, "3ro", 1, "Primaria")));
        when(gradeDomain.hasCourses(3)).thenReturn(true);
        assertThatThrownBy(() -> gradeService.delete(3))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cursos asociados");
    }

    @Test
    void delete_notFound_throws() {
        when(gradeDomain.findById(99)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> gradeService.delete(99))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
