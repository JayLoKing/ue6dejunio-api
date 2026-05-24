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

public record DailyAttendanceRequest(
    @NotNull @JsonProperty("id_grade") Integer gradeId,
    @NotNull @JsonProperty("id_parallel") Integer parallelId,
    @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
    @NotEmpty @Valid List<StudentMark> records
) {
    public record StudentMark(
        @NotNull @JsonProperty("id_student") UUID studentId,
        @NotNull @Pattern(regexp = "Present|Absent|Excused|Late") String status
    ) {}
}
