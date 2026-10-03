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

    /**
     * Whether the gestión holds any attendance dated inside {@code [start, end]} or any score of
     * that trimester. Scores have no date of their own, only a trimester number, so they are
     * matched on it. A cheap EXISTS probe: no rows are loaded.
     */
    boolean hasRecordedActivity(
            Integer academicYearId, int trimester, LocalDate start, LocalDate end);

    /**
     * Whether the gestión holds attendance dated inside the old range {@code [oldStart, oldEnd]}
     * but outside the new one {@code [newStart, newEnd]}, i.e. on a day an edit would stop
     * covering. A cheap EXISTS probe: no rows are loaded.
     */
    boolean hasAttendanceLeftOutside(
            Integer academicYearId,
            LocalDate oldStart,
            LocalDate oldEnd,
            LocalDate newStart,
            LocalDate newEnd);
}
