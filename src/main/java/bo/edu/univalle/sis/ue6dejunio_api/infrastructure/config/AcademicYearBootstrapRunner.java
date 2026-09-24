package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.config;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearDomain;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Ensures an academic_years row exists for the current year (per the injected La Paz Clock) on
 * every startup. Idempotent: does nothing if the row already exists (see
 * AcademicYearRepositoryAdapter#ensureYear). Dependents (course creation, trimester catalog
 * default) resolve the "current academic management" as the LATEST academic year, not this one
 * specifically — this runner only guarantees the row exists.
 */
@Component
@Profile("!test & !it")
public class AcademicYearBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AcademicYearBootstrapRunner.class);

    private final IAcademicYearDomain academicYearDomain;
    private final Clock clock;

    public AcademicYearBootstrapRunner(IAcademicYearDomain academicYearDomain, Clock clock) {
        this.academicYearDomain = academicYearDomain;
        this.clock = clock;
    }

    @Override
    public void run(String... args) {
        int year = LocalDate.now(clock).getYear();
        Integer id = academicYearDomain.ensureYear(year);
        log.info("Bootstrap: ensured academic_years row for year {} (id={}).", year, id);
    }
}
