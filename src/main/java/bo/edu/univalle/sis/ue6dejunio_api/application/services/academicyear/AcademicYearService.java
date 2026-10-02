package bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.AcademicYearItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.CreatedAcademicYear;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcademicYearService implements IAcademicYearService {

    private final IAcademicYearDomain academicYearDomain;

    private final YearResultFreezer freezer;

    private final ICourseEnrollmentDomain enrollmentDomain;

    private final Clock clock;

    public AcademicYearService(
            IAcademicYearDomain academicYearDomain,
            YearResultFreezer freezer,
            ICourseEnrollmentDomain enrollmentDomain,
            Clock clock) {
        this.clock = clock;
        this.academicYearDomain = academicYearDomain;
        this.freezer = freezer;
        this.enrollmentDomain = enrollmentDomain;
    }

    @Override
    @Transactional
    public CreatedAcademicYear create(int year) {
        boolean existed = academicYearDomain.yearExists(year);
        AcademicYearItem item = load(academicYearDomain.ensureYear(year));
        return new CreatedAcademicYear(item, !existed);
    }

    @Override
    @Transactional
    public AcademicYearItem activate(int academicYearId) {
        AcademicYearItem year =
                academicYearDomain
                        .findItem(academicYearId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "AcademicYear", academicYearId));
        if (year.closed()) {
            throw new ConflictException("La gestión está cerrada; reábrala antes de activarla.");
        }
        if (year.active()) {
            return year;
        }
        academicYearDomain.setActive(academicYearId);
        return new AcademicYearItem(year.id(), year.year(), true, false);
    }

    @Override
    @Transactional
    public AcademicYearItem close(int academicYearId) {
        AcademicYearItem year = load(academicYearId);
        if (year.closed()) {
            return year;
        }
        // One moment for both: the year and its enrollments must carry the same closed_at.
        LocalDateTime closedAt = LocalDateTime.now(clock);
        academicYearDomain.close(academicYearId, closedAt);
        freezer.freeze(academicYearId, closedAt);
        return new AcademicYearItem(year.id(), year.year(), false, true);
    }

    @Override
    @Transactional
    public AcademicYearItem reopen(int academicYearId) {
        AcademicYearItem year = load(academicYearId);
        if (!year.closed()) {
            return year;
        }
        // The enrollments reopen with the year (closed_at cleared). Their frozen average and
        // result stay as they were; the next close overwrites them, so nothing is cleared here.
        academicYearDomain.reopen(academicYearId);
        enrollmentDomain.reopenEnrollmentsOfYear(academicYearId);
        return new AcademicYearItem(year.id(), year.year(), year.active(), false);
    }

    private AcademicYearItem load(int academicYearId) {
        return academicYearDomain
                .findItem(academicYearId)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", academicYearId));
    }
}
