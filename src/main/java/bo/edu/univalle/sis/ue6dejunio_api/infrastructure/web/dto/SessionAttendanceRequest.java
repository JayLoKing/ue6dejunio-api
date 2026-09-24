package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.UUID;

public record SessionAttendanceRequest(
        @NotNull @JsonProperty("id_course_enrollment") UUID courseEnrollmentId,
        @NotNull @JsonProperty("id_class_group") UUID classGroupId,
        @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
        @NotNull @Pattern(regexp = "Present|Absent|Excused|Late") String status) {}
