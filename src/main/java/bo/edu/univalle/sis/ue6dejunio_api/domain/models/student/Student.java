package bo.edu.univalle.sis.ue6dejunio_api.domain.models.student;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Student {
    private UUID id;
    private String rudeCode;
    private String identityCard;
    private String names;
    private String lastNames;
    private LocalDate birthDate;
    private String gender;
    private String status;

    /** The category behind the status: "Retiro Voluntario", "Transferencia" or "Otro". */
    private String statusReason;

    /**
     * What the Director wrote. The whole point of the "Otro" category, which says nothing alone.
     */
    private String statusNote;

    private LocalDateTime statusChangedAt;
    private UUID statusChangedById;

    /** Who decided, by name, so a teacher reading the notice knows who to ask. */
    private String statusChangedByName;

    private LocalDateTime createdAt;

    public String fullName() {
        return names + " " + lastNames;
    }
}
