package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.config;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearDomain;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Startup provisioning: ensures the academic_years row for the current year (per injected Clock)
 * exists. Idempotency itself (insert-if-missing) is covered at the adapter level; here we only
 * verify the runner derives the year from the Clock and delegates to the port.
 */
@ExtendWith(MockitoExtension.class)
class AcademicYearBootstrapRunnerTest {

    @Mock private IAcademicYearDomain academicYearDomain;

    @Test
    void run_missingYear_delegatesEnsureYearWithClockYear() throws Exception {
        Clock fixedClock =
                Clock.fixed(Instant.parse("2026-08-15T12:00:00Z"), ZoneId.of("America/La_Paz"));
        when(academicYearDomain.ensureYear(2026)).thenReturn(1);

        AcademicYearBootstrapRunner runner =
                new AcademicYearBootstrapRunner(academicYearDomain, fixedClock);
        runner.run();

        verify(academicYearDomain, times(1)).ensureYear(2026);
    }

    @Test
    void run_presentYear_stillDelegatesToPortNoOpHandledThere() throws Exception {
        Clock fixedClock =
                Clock.fixed(Instant.parse("2027-01-01T12:00:00Z"), ZoneId.of("America/La_Paz"));
        when(academicYearDomain.ensureYear(2027)).thenReturn(5);

        AcademicYearBootstrapRunner runner =
                new AcademicYearBootstrapRunner(academicYearDomain, fixedClock);
        runner.run();

        verify(academicYearDomain, times(1)).ensureYear(2027);
    }
}
