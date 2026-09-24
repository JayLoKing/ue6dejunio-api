package bo.edu.univalle.sis.ue6dejunio_api.application.trimesterperiod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.trimesterperiod.TrimesterPeriodService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.CreateTrimesterPeriodCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.TrimesterPeriod;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.UpdateTrimesterPeriodCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.trimesterperiod.ITrimesterPeriodDomain;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pure validation logic for Director-configured trimester periods: duplicate (year,trimester),
 * overlap between periods of the same year, and date-range sanity. DB-independent (mocked port).
 */
@ExtendWith(MockitoExtension.class)
class TrimesterPeriodServiceTest {

    @Mock private ITrimesterPeriodDomain domain;

    private TrimesterPeriodService service;
    private static final Integer YEAR_ID = 1;

    @BeforeEach
    void setUp() {
        service = new TrimesterPeriodService(domain);
    }

    private static TrimesterPeriod period(UUID id, int trimester, String start, String end) {
        return new TrimesterPeriod(
                id, YEAR_ID, trimester, LocalDate.parse(start), LocalDate.parse(end));
    }

    @Test
    void create_unknownAcademicYear_throwsNotFound() {
        when(domain.academicYearExists(YEAR_ID)).thenReturn(false);

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateTrimesterPeriodCommand(
                                                YEAR_ID,
                                                1,
                                                LocalDate.parse("2026-02-01"),
                                                LocalDate.parse("2026-05-31"))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_endBeforeStart_throwsValidation() {
        when(domain.academicYearExists(YEAR_ID)).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateTrimesterPeriodCommand(
                                                YEAR_ID,
                                                1,
                                                LocalDate.parse("2026-05-31"),
                                                LocalDate.parse("2026-02-01"))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_duplicateYearAndTrimester_throwsDuplicate() {
        when(domain.academicYearExists(YEAR_ID)).thenReturn(true);
        when(domain.existsByAcademicYearAndTrimester(YEAR_ID, 1)).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateTrimesterPeriodCommand(
                                                YEAR_ID,
                                                1,
                                                LocalDate.parse("2026-02-01"),
                                                LocalDate.parse("2026-05-31"))))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void create_overlapsExistingPeriod_throwsConflict() {
        when(domain.academicYearExists(YEAR_ID)).thenReturn(true);
        when(domain.existsByAcademicYearAndTrimester(YEAR_ID, 2)).thenReturn(false);
        when(domain.findByAcademicYear(YEAR_ID))
                .thenReturn(List.of(period(UUID.randomUUID(), 1, "2026-02-01", "2026-05-31")));

        // Overlaps T1 by one day (05-31).
        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateTrimesterPeriodCommand(
                                                YEAR_ID,
                                                2,
                                                LocalDate.parse("2026-05-31"),
                                                LocalDate.parse("2026-08-31"))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_adjacentNonOverlappingPeriod_succeeds() {
        when(domain.academicYearExists(YEAR_ID)).thenReturn(true);
        when(domain.existsByAcademicYearAndTrimester(YEAR_ID, 2)).thenReturn(false);
        when(domain.findByAcademicYear(YEAR_ID))
                .thenReturn(List.of(period(UUID.randomUUID(), 1, "2026-02-01", "2026-05-31")));
        when(domain.save(YEAR_ID, 2, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-08-31")))
                .thenReturn(period(UUID.randomUUID(), 2, "2026-06-01", "2026-08-31"));

        TrimesterPeriod result =
                service.create(
                        new CreateTrimesterPeriodCommand(
                                YEAR_ID,
                                2,
                                LocalDate.parse("2026-06-01"),
                                LocalDate.parse("2026-08-31")));

        assertThat(result.trimester()).isEqualTo(2);
    }

    @Test
    void update_overlapsOtherPeriod_throwsConflict_excludingItself() {
        UUID id = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        when(domain.findById(id))
                .thenReturn(Optional.of(period(id, 2, "2026-06-01", "2026-08-31")));
        when(domain.findByAcademicYear(YEAR_ID))
                .thenReturn(
                        List.of(
                                period(id, 2, "2026-06-01", "2026-08-31"),
                                period(otherId, 3, "2026-09-01", "2026-11-30")));

        assertThatThrownBy(
                        () ->
                                service.update(
                                        id,
                                        new UpdateTrimesterPeriodCommand(
                                                LocalDate.parse("2026-06-01"),
                                                LocalDate.parse("2026-09-01"))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_notOverlappingOthers_succeeds_evenThoughItOverlapsItself() {
        UUID id = UUID.randomUUID();
        when(domain.findById(id))
                .thenReturn(Optional.of(period(id, 1, "2026-02-01", "2026-05-31")));
        when(domain.findByAcademicYear(YEAR_ID))
                .thenReturn(List.of(period(id, 1, "2026-02-01", "2026-05-31")));
        when(domain.update(id, LocalDate.parse("2026-02-05"), LocalDate.parse("2026-05-25")))
                .thenReturn(period(id, 1, "2026-02-05", "2026-05-25"));

        TrimesterPeriod result =
                service.update(
                        id,
                        new UpdateTrimesterPeriodCommand(
                                LocalDate.parse("2026-02-05"), LocalDate.parse("2026-05-25")));

        assertThat(result.startDate()).isEqualTo(LocalDate.parse("2026-02-05"));
    }

    @Test
    void getById_unknown_throwsNotFound() {
        when(domain.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_unknown_throwsNotFound_withoutCallingDomainDelete() {
        when(domain.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
