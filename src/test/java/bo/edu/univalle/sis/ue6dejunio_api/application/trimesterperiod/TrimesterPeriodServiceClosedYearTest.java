package bo.edu.univalle.sis.ue6dejunio_api.application.trimesterperiod;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.trimesterperiod.TrimesterPeriodService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.CreateTrimesterPeriodCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.TrimesterPeriod;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.UpdateTrimesterPeriodCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.trimesterperiod.ITrimesterPeriodDomain;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The period carries the year row id, so the guard is asked by year and not by a record. */
@ExtendWith(MockitoExtension.class)
class TrimesterPeriodServiceClosedYearTest {

    private static final Integer YEAR_ID = 7;

    @Mock private ITrimesterPeriodDomain domain;
    @Mock private ClosedYearGuard closedYearGuard;

    private final UUID id = UUID.randomUUID();

    private TrimesterPeriodService service() {
        return new TrimesterPeriodService(domain, closedYearGuard);
    }

    private void yearIsClosed() {
        doThrow(new ConflictException("La gestión 2026 está cerrada; no admite cambios."))
                .when(closedYearGuard)
                .requireOpenForYear(YEAR_ID);
    }

    private TrimesterPeriod period() {
        return new TrimesterPeriod(
                id, YEAR_ID, 1, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 5, 31));
    }

    @Test
    void create_inAClosedYear_isRefusedBeforeAnyWrite() {
        when(domain.academicYearExists(YEAR_ID)).thenReturn(true);
        yearIsClosed();
        CreateTrimesterPeriodCommand command =
                new CreateTrimesterPeriodCommand(
                        YEAR_ID, 1, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 5, 31));

        assertThatThrownBy(() -> service().create(command))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cerrada");

        verify(domain, never()).save(any(), anyInt(), any(), any());
    }

    @Test
    void update_inAClosedYear_isRefusedAndChangesNothing() {
        when(domain.findById(id)).thenReturn(Optional.of(period()));
        yearIsClosed();
        UpdateTrimesterPeriodCommand command =
                new UpdateTrimesterPeriodCommand(
                        LocalDate.of(2026, 2, 2), LocalDate.of(2026, 5, 30));

        assertThatThrownBy(() -> service().update(id, command))
                .isInstanceOf(ConflictException.class);

        verify(domain, never()).update(any(), any(), any());
    }

    @Test
    void delete_inAClosedYear_isRefusedAndRemovesNothing() {
        when(domain.findById(id)).thenReturn(Optional.of(period()));
        yearIsClosed();

        assertThatThrownBy(() -> service().delete(id)).isInstanceOf(ConflictException.class);

        verify(domain, never()).delete(any());
    }
}
