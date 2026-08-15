package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.TrimesterPeriod;

import java.time.LocalDate;
import java.util.UUID;

// Plain camelCase, matching the existing DTO convention across the codebase
// (e.g. CourseResponse, UserResponse, GradeResponse) — no @JsonProperty overrides.
public record TrimesterPeriodResponse(
    UUID id,
    Integer academicYearId,
    Integer trimester,
    LocalDate startDate,
    LocalDate endDate
) {
    public static TrimesterPeriodResponse from(TrimesterPeriod p) {
        return new TrimesterPeriodResponse(p.id(), p.academicYearId(), p.trimester(),
            p.startDate(), p.endDate());
    }
}
