package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @param statusReason the category behind the status
 * @param statusNote what the Director wrote, when the category did not say enough on its own
 * @param statusChangedByName who decided, by name, so the teacher reading it knows who to ask
 */
public record StudentResponse(
        UUID id,
        String rudeCode,
        String identityCard,
        String names,
        String lastNames,
        LocalDate birthDate,
        String gender,
        String status,
        String statusReason,
        String statusNote,
        LocalDateTime statusChangedAt,
        UUID statusChangedById,
        String statusChangedByName,
        LocalDateTime createdAt) {
    public static StudentResponse from(Student s) {
        return new StudentResponse(
                s.getId(),
                s.getRudeCode(),
                s.getIdentityCard(),
                s.getNames(),
                s.getLastNames(),
                s.getBirthDate(),
                s.getGender(),
                s.getStatus(),
                s.getStatusReason(),
                s.getStatusNote(),
                s.getStatusChangedAt(),
                s.getStatusChangedById(),
                s.getStatusChangedByName(),
                s.getCreatedAt());
    }
}
