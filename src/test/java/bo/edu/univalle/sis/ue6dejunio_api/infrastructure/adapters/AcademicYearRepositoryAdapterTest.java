package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.AcademicYearItem;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AcademicYearEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaAcademicYearRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Idempotent auto-provisioning of academic_years rows and "current = active year" resolution.
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
    void currentYearId_returnsTheActiveYearNotTheLatest() {
        AcademicYearEntity active = new AcademicYearEntity();
        active.setId(4);
        active.setYear(2026);
        active.setActive(true);
        when(yearRepo.findByActiveTrue()).thenReturn(Optional.of(active));

        Integer id = adapter.currentYearId();

        assertThat(id).isEqualTo(4);
    }

    @Test
    void currentYearId_noneActive_throwsResourceNotFound() {
        when(yearRepo.findByActiveTrue()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.currentYearId())
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void activateIfNoneActive_nothingActive_activatesThatYear() {
        AcademicYearEntity row = new AcademicYearEntity();
        row.setId(7);
        row.setYear(2026);
        when(yearRepo.existsByActiveTrue()).thenReturn(false);
        when(yearRepo.findById(7)).thenReturn(Optional.of(row));

        boolean activated = adapter.activateIfNoneActive(7);

        assertThat(activated).isTrue();
        assertThat(row.isActive()).isTrue();
        verify(yearRepo).save(row);
    }

    @Test
    void activateIfNoneActive_anotherYearIsActive_changesNothing() {
        when(yearRepo.existsByActiveTrue()).thenReturn(true);

        boolean activated = adapter.activateIfNoneActive(7);

        assertThat(activated).isFalse();
        verify(yearRepo, never()).save(any(AcademicYearEntity.class));
    }

    @Test
    void activateIfNoneActive_unknownYear_throwsResourceNotFound() {
        when(yearRepo.existsByActiveTrue()).thenReturn(false);
        when(yearRepo.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.activateIfNoneActive(99))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(yearRepo, never()).save(any(AcademicYearEntity.class));
    }

    @Test
    void setActive_clearsTheCurrentActiveRowBeforeSettingTheTarget() {
        adapter.setActive(4);

        // The unique index is checked row by row: the old row must be cleared first.
        InOrder order = inOrder(yearRepo);
        order.verify(yearRepo).deactivateAllExcept(4);
        order.verify(yearRepo).activate(4);
    }

    @Test
    void findItem_mapsActiveAndClosed() {
        AcademicYearEntity closed = new AcademicYearEntity();
        closed.setId(2);
        closed.setYear(2025);
        closed.setClosedAt(LocalDateTime.of(2025, 12, 1, 8, 0));
        when(yearRepo.findById(2)).thenReturn(Optional.of(closed));

        assertThat(adapter.findItem(2)).contains(new AcademicYearItem(2, 2025, false, true));
    }

    @Test
    void findItem_unknownId_isEmpty() {
        when(yearRepo.findById(9)).thenReturn(Optional.empty());

        assertThat(adapter.findItem(9)).isEmpty();
    }
}
