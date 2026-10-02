package bo.edu.univalle.sis.ue6dejunio_api.application.academicyear;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.AcademicYearService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.AcademicYearItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearDomain;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The Director's choice of working year: the rules around it, with the port mocked. */
@ExtendWith(MockitoExtension.class)
class AcademicYearServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-02T16:00:00Z"), ZoneId.of("America/La_Paz"));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 12, 0);

    @Mock private IAcademicYearDomain domain;

    private AcademicYearService service;

    @BeforeEach
    void setUp() {
        service = new AcademicYearService(domain, CLOCK);
    }

    @Test
    void activate_unknownYear_isNotFound() {
        when(domain.findItem(9)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activate(9)).isInstanceOf(ResourceNotFoundException.class);

        verify(domain, never()).setActive(9);
    }

    @Test
    void activate_closedYear_isAConflictAndChangesNothing() {
        when(domain.findItem(2))
                .thenReturn(Optional.of(new AcademicYearItem(2, 2025, false, true)));

        assertThatThrownBy(() -> service.activate(2))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cerrada");

        verify(domain, never()).setActive(2);
    }

    @Test
    void activate_alreadyActive_returnsItWithoutWriting() {
        AcademicYearItem active = new AcademicYearItem(1, 2026, true, false);
        when(domain.findItem(1)).thenReturn(Optional.of(active));

        assertThat(service.activate(1)).isEqualTo(active);

        verify(domain, never()).setActive(1);
    }

    @Test
    void activate_openInactiveYear_switchesThroughThePortAndReturnsItActive() {
        when(domain.findItem(2))
                .thenReturn(Optional.of(new AcademicYearItem(2, 2025, false, false)));

        AcademicYearItem result = service.activate(2);

        verify(domain).setActive(2);
        assertThat(result).isEqualTo(new AcademicYearItem(2, 2025, true, false));
    }

    @Test
    void close_unknownYear_isNotFound() {
        when(domain.findItem(9)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.close(9)).isInstanceOf(ResourceNotFoundException.class);

        verify(domain, never()).close(anyInt(), any());
    }

    @Test
    void close_activeOpenYear_closesAtTheClockAndReturnsItClosedAndInactive() {
        when(domain.findItem(1))
                .thenReturn(Optional.of(new AcademicYearItem(1, 2026, true, false)));

        AcademicYearItem result = service.close(1);

        verify(domain).close(1, NOW);
        assertThat(result).isEqualTo(new AcademicYearItem(1, 2026, false, true));
    }

    @Test
    void close_alreadyClosed_isANoOpThatKeepsTheOriginalClosingMoment() {
        AcademicYearItem closed = new AcademicYearItem(2, 2025, false, true);
        when(domain.findItem(2)).thenReturn(Optional.of(closed));

        assertThat(service.close(2)).isEqualTo(closed);

        verify(domain, never()).close(anyInt(), any());
    }

    @Test
    void reopen_unknownYear_isNotFound() {
        when(domain.findItem(9)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reopen(9)).isInstanceOf(ResourceNotFoundException.class);

        verify(domain, never()).reopen(anyInt());
    }

    @Test
    void reopen_closedYear_opensItWithoutActivatingIt() {
        when(domain.findItem(2))
                .thenReturn(Optional.of(new AcademicYearItem(2, 2025, false, true)));

        AcademicYearItem result = service.reopen(2);

        verify(domain).reopen(2);
        verify(domain, never()).setActive(anyInt());
        assertThat(result).isEqualTo(new AcademicYearItem(2, 2025, false, false));
    }

    @Test
    void reopen_alreadyOpen_isANoOp() {
        AcademicYearItem open = new AcademicYearItem(1, 2026, true, false);
        when(domain.findItem(1)).thenReturn(Optional.of(open));

        assertThat(service.reopen(1)).isEqualTo(open);

        verify(domain, never()).reopen(anyInt());
    }
}
