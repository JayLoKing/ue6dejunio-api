package bo.edu.univalle.sis.ue6dejunio_api.application.level;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.level.LevelService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.level.CreateLevelCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.level.Level;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.level.UpdateLevelCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.level.ILevelDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LevelServiceTest {

    @Mock private ILevelDomain levelDomain;
    @InjectMocks private LevelService levelService;

    @Test
    void create_success() {
        when(levelDomain.existsByName("Primaria")).thenReturn(false);
        when(levelDomain.save(any(Level.class))).thenReturn(new Level(1, "Primaria"));
        Level r = levelService.create(new CreateLevelCommand("Primaria"));
        assertThat(r.name()).isEqualTo("Primaria");
    }

    @Test
    void create_duplicate_throws() {
        when(levelDomain.existsByName("Primaria")).thenReturn(true);
        assertThatThrownBy(() -> levelService.create(new CreateLevelCommand("Primaria")))
            .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void update_renameToExisting_throws() {
        when(levelDomain.findById(1)).thenReturn(Optional.of(new Level(1, "Primaria")));
        when(levelDomain.existsByName("Secundaria")).thenReturn(true);
        assertThatThrownBy(() -> levelService.update(1, new UpdateLevelCommand("Secundaria")))
            .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void delete_withGrades_throws() {
        when(levelDomain.findById(1)).thenReturn(Optional.of(new Level(1, "Primaria")));
        when(levelDomain.hasGrades(1)).thenReturn(true);
        assertThatThrownBy(() -> levelService.delete(1))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void getById_notFound_throws() {
        when(levelDomain.findById(9)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> levelService.getById(9))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
