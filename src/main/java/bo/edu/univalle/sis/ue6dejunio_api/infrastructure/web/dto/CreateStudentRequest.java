package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateStudentRequest(
        @NotBlank @Size(max = 20) String rudeCode,
        @NotBlank @Size(max = 15) String identityCard,
        @NotBlank @Size(max = 100) String names,
        @NotBlank @Size(max = 100) String lastNames,
        @NotNull @Past @JsonFormat(pattern = "yyyy-MM-dd") LocalDate birthDate,
        @NotBlank @Pattern(regexp = "^[MF]$", message = "gender debe ser M o F") String gender) {}
