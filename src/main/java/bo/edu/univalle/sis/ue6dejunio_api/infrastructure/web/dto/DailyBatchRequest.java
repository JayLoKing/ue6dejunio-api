package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DailyBatchRequest(
    @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
    @NotEmpty @Valid List<Mark> records
) {
    public record Mark(
        @NotNull @JsonProperty("id_course_enrollment") UUID courseEnrollmentId,
        @NotNull @Pattern(regexp = "Present|Absent|Excused|Late") String status
    ) {}
}
