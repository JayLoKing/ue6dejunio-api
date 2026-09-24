package bo.edu.univalle.sis.ue6dejunio_api.application.parallel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.parallel.ParallelService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.parallel.CreateParallelCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.parallel.Parallel;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.parallel.IParallelDomain;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParallelServiceTest {

    @Mock private IParallelDomain parallelDomain;
    @InjectMocks private ParallelService parallelService;

    @Test
    void create_success() {
        when(parallelDomain.existsByName("A")).thenReturn(false);
        when(parallelDomain.save(any(Parallel.class))).thenReturn(new Parallel(1, "A"));
        Parallel r = parallelService.create(new CreateParallelCommand("A"));
        assertThat(r.name()).isEqualTo("A");
    }

    @Test
    void create_duplicate_throws() {
        when(parallelDomain.existsByName("A")).thenReturn(true);
        assertThatThrownBy(() -> parallelService.create(new CreateParallelCommand("A")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void delete_withClassGroups_throws() {
        when(parallelDomain.findById(1)).thenReturn(Optional.of(new Parallel(1, "A")));
        when(parallelDomain.hasClassGroups(1)).thenReturn(true);
        assertThatThrownBy(() -> parallelService.delete(1)).isInstanceOf(ConflictException.class);
    }
}
