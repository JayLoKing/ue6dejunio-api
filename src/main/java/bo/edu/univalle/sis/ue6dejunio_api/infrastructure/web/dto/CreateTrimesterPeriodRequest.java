package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

// Snake_case @JsonProperty, matching the existing Request DTO convention across the codebase
// (e.g. CreateCourseRequest, HomeroomTeacherRequest, DailyAttendanceRequest).
public record CreateTrimesterPeriodRequest(
        @NotNull @Positive @JsonProperty("id_academic_year") Integer academicYearId,
        @NotNull @Min(1) @Max(3) Integer trimester,
        @NotNull @JsonProperty("start_date") LocalDate startDate,
        @NotNull @JsonProperty("end_date") LocalDate endDate) {}
