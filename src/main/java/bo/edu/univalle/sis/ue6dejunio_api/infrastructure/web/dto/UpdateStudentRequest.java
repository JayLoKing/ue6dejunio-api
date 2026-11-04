package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDetails;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * The edit form, whole: what it sends is what the student becomes, so an optional field left empty
 * clears it. Same rules as a roster row — names required, the rest optional, a blank field null and
 * any other one trimmed.
 *
 * @param rudeCode optional
 * @param identityCard optional
 * @param birthDate optional
 * @param gender optional; M or F when present
 */
public record UpdateStudentRequest(
        @Size(max = 20) String rudeCode,
        @Size(max = 15) String identityCard,
        @NotBlank @Size(max = 100) String names,
        @NotBlank @Size(max = 100) String lastNames,
        @Past @JsonFormat(pattern = "yyyy-MM-dd") LocalDate birthDate,
        @Pattern(regexp = "^[MF]$", message = "gender debe ser M o F") String gender) {

    public UpdateStudentRequest {
        rudeCode = CreateStudentRequest.blankToNull(rudeCode);
        identityCard = CreateStudentRequest.blankToNull(identityCard);
        names = CreateStudentRequest.blankToNull(names);
        lastNames = CreateStudentRequest.blankToNull(lastNames);
        gender = CreateStudentRequest.blankToNull(gender);
    }

    public StudentDetails toDetails() {
        return new StudentDetails(rudeCode, identityCard, names, lastNames, birthDate, gender);
    }
}
