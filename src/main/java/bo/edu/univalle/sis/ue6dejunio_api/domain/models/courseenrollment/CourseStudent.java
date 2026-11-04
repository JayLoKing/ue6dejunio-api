package bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param birthDate carried so the roster can say whether the record is complete. Null for a student
 *     registered from the name-only roster
 */
public record CourseStudent(
        UUID courseEnrollmentId,
        UUID studentId,
        String rudeCode,
        String identityCard,
        String names,
        String lastNames,
        String status,
        String gender,
        LocalDate birthDate) {
    public String fullName() {
        return names + " " + lastNames;
    }

    /** Whether RUDE, CI, birth date or gender is still unknown. */
    public boolean incomplete() {
        return Student.identityIncomplete(rudeCode, identityCard, birthDate, gender);
    }
}
