package bo.edu.univalle.sis.ue6dejunio_api.application.catalog;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.catalog.CatalogService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.TrimesterPeriodItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.TrimesterPeriod;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.catalog.ICatalogDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.trimesterperiod.ITrimesterPeriodDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Catalog exposure of Director-configured trimester periods. When no academic year is given,
 * defaults to the current (latest) academic year via IAcademicYearDomain.
 */
@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock private ICatalogDomain catalogDomain;
    @Mock private ITrimesterPeriodDomain trimesterPeriodDomain;
    @Mock private IAcademicYearDomain academicYearDomain;

    private CatalogService service;

    @Test
    void trimesters_explicitYear_returnsMappedItemsOrderedByTrimester() {
        service = new CatalogService(catalogDomain, trimesterPeriodDomain, academicYearDomain);
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        List<TrimesterPeriod> periods = List.of(
            new TrimesterPeriod(id1, 5, 1, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 5, 31)),
            new TrimesterPeriod(id2, 5, 2, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31))
        );
        when(trimesterPeriodDomain.findByAcademicYear(5)).thenReturn(periods);

        List<TrimesterPeriodItem> result = service.trimesters(5);

        assertThat(result).containsExactly(
            new TrimesterPeriodItem(id1, 1, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 5, 31)),
            new TrimesterPeriodItem(id2, 2, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31))
        );
        verify(academicYearDomain, never()).currentYearId();
    }

    @Test
    void trimesters_nullYear_defaultsToCurrentAcademicYear() {
        service = new CatalogService(catalogDomain, trimesterPeriodDomain, academicYearDomain);
        when(academicYearDomain.currentYearId()).thenReturn(9);
        when(trimesterPeriodDomain.findByAcademicYear(9)).thenReturn(List.of());

        List<TrimesterPeriodItem> result = service.trimesters(null);

        assertThat(result).isEmpty();
        verify(trimesterPeriodDomain).findByAcademicYear(9);
    }
}
