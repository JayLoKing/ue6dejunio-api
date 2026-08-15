package bo.edu.univalle.sis.ue6dejunio_api.application.services.trimesterperiod;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.CreateTrimesterPeriodCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.TrimesterPeriod;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.UpdateTrimesterPeriodCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.trimesterperiod.ITrimesterPeriodDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.trimesterperiod.ITrimesterPeriodService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class TrimesterPeriodService implements ITrimesterPeriodService {

    private final ITrimesterPeriodDomain trimesterPeriodDomain;

    public TrimesterPeriodService(ITrimesterPeriodDomain trimesterPeriodDomain) {
        this.trimesterPeriodDomain = trimesterPeriodDomain;
    }

    @Override
    @Transactional
    public TrimesterPeriod create(CreateTrimesterPeriodCommand c) {
        if (!trimesterPeriodDomain.academicYearExists(c.academicYearId())) {
            throw new ResourceNotFoundException("AcademicYear", c.academicYearId());
        }
        validateDateRange(c.startDate(), c.endDate());
        if (trimesterPeriodDomain.existsByAcademicYearAndTrimester(c.academicYearId(), c.trimester())) {
            throw new DuplicateResourceException("id_academic_year+trimester",
                c.academicYearId() + "-" + c.trimester());
        }
        List<TrimesterPeriod> existing = trimesterPeriodDomain.findByAcademicYear(c.academicYearId());
        requireNoOverlap(existing, c.startDate(), c.endDate(), null);
        return trimesterPeriodDomain.save(c.academicYearId(), c.trimester(), c.startDate(), c.endDate());
    }

    @Override
    @Transactional
    public TrimesterPeriod update(UUID id, UpdateTrimesterPeriodCommand c) {
        TrimesterPeriod current = getById(id);
        validateDateRange(c.startDate(), c.endDate());
        List<TrimesterPeriod> existing = trimesterPeriodDomain.findByAcademicYear(current.academicYearId());
        requireNoOverlap(existing, c.startDate(), c.endDate(), id);
        return trimesterPeriodDomain.update(id, c.startDate(), c.endDate());
    }

    @Override
    @Transactional(readOnly = true)
    public TrimesterPeriod getById(UUID id) {
        return trimesterPeriodDomain.findById(id)
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
        getById(id);
        trimesterPeriodDomain.delete(id);
    }

    private static void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new ValidationException("end_date debe ser mayor o igual a start_date");
        }
    }

    /** Overlap check between the (up to 3) periods of a single academic year. */
    private static void requireNoOverlap(List<TrimesterPeriod> existing, LocalDate startDate,
                                         LocalDate endDate, UUID excludeId) {
        for (TrimesterPeriod p : existing) {
            if (excludeId != null && p.id().equals(excludeId)) {
                continue;
            }
            if (p.overlaps(startDate, endDate)) {
                throw new ConflictException(
                    "El rango de fechas se superpone con el trimestre " + p.trimester()
                        + " (" + p.startDate() + " a " + p.endDate() + ")");
            }
        }
    }
}
