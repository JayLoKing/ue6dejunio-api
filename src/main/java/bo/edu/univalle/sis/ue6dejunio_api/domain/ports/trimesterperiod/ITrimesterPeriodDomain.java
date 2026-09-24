package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.trimesterperiod;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.TrimesterPeriod;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ITrimesterPeriodDomain {
    TrimesterPeriod save(
            Integer academicYearId, int trimester, LocalDate startDate, LocalDate endDate);

    TrimesterPeriod update(UUID id, LocalDate startDate, LocalDate endDate);

    void delete(UUID id);

    Optional<TrimesterPeriod> findById(UUID id);

    /** All periods (up to 3) configured for the academic year, ordered by trimester. */
    List<TrimesterPeriod> findByAcademicYear(Integer academicYearId);

    boolean academicYearExists(Integer academicYearId);

    boolean existsByAcademicYearAndTrimester(Integer academicYearId, int trimester);
}
