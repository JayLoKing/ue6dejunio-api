package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * One roster row. Only the names are required: the school's name-only roster carries nothing else,
 * and the SIE roster or a hand edit completes the student later.
 *
 * <p>A blank optional field arrives as null, trimmed otherwise. An empty RUDE or CI cell means "not
 * known", and stored as an empty string it would be a code — one that the UNIQUE constraint lets
 * only a single student hold.
 *
 * @param rudeCode optional
 * @param identityCard optional
 * @param birthDate optional
 * @param gender optional; M or F when present
 */
public record CreateStudentRequest(
        @Size(max = 20) String rudeCode,
        @Size(max = 15) String identityCard,
        @NotBlank @Size(max = 100) String names,
        @NotBlank @Size(max = 100) String lastNames,
        @Past @JsonFormat(pattern = "yyyy-MM-dd") LocalDate birthDate,
        @Pattern(regexp = "^[MF]$", message = "gender debe ser M o F") String gender) {

    public CreateStudentRequest {
        rudeCode = blankToNull(rudeCode);
        identityCard = blankToNull(identityCard);
        gender = blankToNull(gender);
    }

    /** Blank means unknown; anything else is kept, trimmed. */
    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
