package bo.edu.univalle.sis.ue6dejunio_api.application.grade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.grade.GradeService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.grade.CreateGradeCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.grade.Grade;
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
        when(gradeDomain.countTotal()).thenReturn(5L);
        when(gradeDomain.levelExists(1)).thenReturn(true);
        when(gradeDomain.existsByNameAndLevel("1ro", 1)).thenReturn(false);
        when(gradeDomain.create("1ro", 1)).thenReturn(new Grade(7, "1ro", 1, "Primaria"));

        Grade result = gradeService.create(cmd());

        assertThat(result.name()).isEqualTo("1ro");
    }

    @Test
    void create_sixthLimitReached_throws() {
        when(gradeDomain.countTotal()).thenReturn(6L);
        assertThatThrownBy(() -> gradeService.create(cmd()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("6");
    }

    @Test
    void create_levelNotFound_throws() {
        when(gradeDomain.countTotal()).thenReturn(0L);
        when(gradeDomain.levelExists(1)).thenReturn(false);
        assertThatThrownBy(() -> gradeService.create(cmd()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_duplicateNameInLevel_throws() {
        when(gradeDomain.countTotal()).thenReturn(0L);
        when(gradeDomain.levelExists(1)).thenReturn(true);
        when(gradeDomain.existsByNameAndLevel("1ro", 1)).thenReturn(true);
        assertThatThrownBy(() -> gradeService.create(cmd()))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void delete_withClassGroups_throws() {
        when(gradeDomain.findById(3)).thenReturn(Optional.of(new Grade(3, "3ro", 1, "Primaria")));
        when(gradeDomain.hasClassGroups(3)).thenReturn(true);
        assertThatThrownBy(() -> gradeService.delete(3)).isInstanceOf(ConflictException.class);
    }

    @Test
    void delete_notFound_throws() {
        when(gradeDomain.findById(99)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> gradeService.delete(99))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
