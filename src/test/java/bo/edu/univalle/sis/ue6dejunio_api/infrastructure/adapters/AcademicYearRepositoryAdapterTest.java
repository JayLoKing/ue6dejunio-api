package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AcademicYearEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaAcademicYearRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Idempotent auto-provisioning of academic_years rows and "current = latest year" resolution.
 * DB-independent (mocked JPA repository).
 */
@ExtendWith(MockitoExtension.class)
class AcademicYearRepositoryAdapterTest {

    @Mock private JpaAcademicYearRepository yearRepo;
    @InjectMocks private AcademicYearRepositoryAdapter adapter;

    @Test
    void ensureYear_missing_insertsNewRow() {
        when(yearRepo.findByYear(2026)).thenReturn(Optional.empty());
        AcademicYearEntity saved = new AcademicYearEntity();
        saved.setId(7);
        saved.setYear(2026);
        when(yearRepo.save(any(AcademicYearEntity.class))).thenReturn(saved);

        Integer id = adapter.ensureYear(2026);

        assertThat(id).isEqualTo(7);
        verify(yearRepo, times(1)).save(any(AcademicYearEntity.class));
    }

    @Test
    void ensureYear_present_returnsExistingIdWithoutInserting() {
        AcademicYearEntity existing = new AcademicYearEntity();
        existing.setId(3);
        existing.setYear(2026);
        when(yearRepo.findByYear(2026)).thenReturn(Optional.of(existing));

        Integer id = adapter.ensureYear(2026);

        assertThat(id).isEqualTo(3);
        verify(yearRepo, never()).save(any(AcademicYearEntity.class));
    }

    @Test
    void currentYearId_returnsLatestYearId() {
        AcademicYearEntity latest = new AcademicYearEntity();
        latest.setId(9);
        latest.setYear(2027);
        when(yearRepo.findTopByOrderByYearDesc()).thenReturn(Optional.of(latest));

        Integer id = adapter.currentYearId();

        assertThat(id).isEqualTo(9);
    }

    @Test
    void currentYearId_none_throwsResourceNotFound() {
        when(yearRepo.findTopByOrderByYearDesc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.currentYearId())
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
