package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

// Snake_case @JsonProperty, matching the existing Request DTO convention across the codebase
// (e.g. UpdateCourseRequest, DailyAttendanceRequest).
public record UpdateTrimesterPeriodRequest(
        @NotNull @JsonProperty("start_date") LocalDate startDate,
        @NotNull @JsonProperty("end_date") LocalDate endDate) {}
