package bo.edu.univalle.sis.ue6dejunio_api.application.services.trimesterperiod;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.CreateTrimesterPeriodCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.TrimesterPeriod;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.UpdateTrimesterPeriodCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.trimesterperiod.ITrimesterPeriodDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.trimesterperiod.ITrimesterPeriodService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrimesterPeriodService implements ITrimesterPeriodService {

    private final ITrimesterPeriodDomain trimesterPeriodDomain;
    private final ClosedYearGuard closedYearGuard;

    public TrimesterPeriodService(
            ITrimesterPeriodDomain trimesterPeriodDomain, ClosedYearGuard closedYearGuard) {
        this.trimesterPeriodDomain = trimesterPeriodDomain;
        this.closedYearGuard = closedYearGuard;
    }

    @Override
    @Transactional
    public TrimesterPeriod create(CreateTrimesterPeriodCommand c) {
        if (!trimesterPeriodDomain.academicYearExists(c.academicYearId())) {
            throw new ResourceNotFoundException("AcademicYear", c.academicYearId());
        }
        closedYearGuard.requireOpenForYear(c.academicYearId());
        validateDateRange(c.startDate(), c.endDate());
        if (trimesterPeriodDomain.existsByAcademicYearAndTrimester(
                c.academicYearId(), c.trimester())) {
            throw new DuplicateResourceException(
                    "id_academic_year+trimester", c.academicYearId() + "-" + c.trimester());
        }
        List<TrimesterPeriod> existing =
                trimesterPeriodDomain.findByAcademicYear(c.academicYearId());
        requireNoOverlap(existing, c.startDate(), c.endDate(), null);
        return trimesterPeriodDomain.save(
                c.academicYearId(), c.trimester(), c.startDate(), c.endDate());
    }

    @Override
    @Transactional
    public TrimesterPeriod update(UUID id, UpdateTrimesterPeriodCommand c) {
        TrimesterPeriod current = getById(id);
        closedYearGuard.requireOpenForYear(current.academicYearId());
        validateDateRange(c.startDate(), c.endDate());
        List<TrimesterPeriod> existing =
                trimesterPeriodDomain.findByAcademicYear(current.academicYearId());
        requireNoOverlap(existing, c.startDate(), c.endDate(), id);
        requireNoAttendanceLeftOutside(current, c);
        return trimesterPeriodDomain.update(id, c.startDate(), c.endDate());
    }

    @Override
    @Transactional(readOnly = true)
    public TrimesterPeriod getById(UUID id) {
        return trimesterPeriodDomain
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TrimesterPeriod", id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrimesterPeriod> listByAcademicYear(Integer academicYearId) {
        return trimesterPeriodDomain.findByAcademicYear(academicYearId);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        TrimesterPeriod period = getById(id);
        closedYearGuard.requireOpenForYear(period.academicYearId());
        // The course panel and the attendance report resolve their scope from this row, so
        // dropping it under recorded data would orphan that data from every report.
        if (trimesterPeriodDomain.hasRecordedActivity(
                period.academicYearId(),
                period.trimester(),
                period.startDate(),
                period.endDate())) {
            throw new ConflictException(
                    "No se puede eliminar el trimestre: tiene asistencias o calificaciones"
                            + " registradas");
        }
        trimesterPeriodDomain.delete(id);
    }

    /**
     * Extending a period is always safe. Shrinking or shifting it is refused only when attendance
     * sits on a day the old range covered and the new one does not: that record would be left
     * outside every report scoped by this period. Scores are unaffected (matched by trimester
     * number), so only attendance is probed.
     */
    private void requireNoAttendanceLeftOutside(
            TrimesterPeriod current, UpdateTrimesterPeriodCommand c) {
        boolean extendsOnly =
                !c.startDate().isAfter(current.startDate())
                        && !c.endDate().isBefore(current.endDate());
        if (extendsOnly) {
            return;
        }
        if (trimesterPeriodDomain.hasAttendanceLeftOutside(
                current.academicYearId(),
                current.startDate(),
                current.endDate(),
                c.startDate(),
                c.endDate())) {
            throw new ConflictException(
                    "No se puede acortar el trimestre: hay asistencias registradas en las fechas"
                            + " que quedarían fuera");
        }
    }

    private static void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new ValidationException("end_date debe ser mayor o igual a start_date");
        }
    }

    /** Overlap check between the (up to 3) periods of a single academic year. */
    private static void requireNoOverlap(
            List<TrimesterPeriod> existing,
            LocalDate startDate,
            LocalDate endDate,
            UUID excludeId) {
        for (TrimesterPeriod p : existing) {
            if (excludeId != null && p.id().equals(excludeId)) {
                continue;
            }
            if (p.overlaps(startDate, endDate)) {
                throw new ConflictException(
                        "El rango de fechas se superpone con el trimestre "
                                + p.trimester()
                                + " ("
                                + p.startDate()
                                + " a "
                                + p.endDate()
                                + ")");
            }
        }
    }
}
