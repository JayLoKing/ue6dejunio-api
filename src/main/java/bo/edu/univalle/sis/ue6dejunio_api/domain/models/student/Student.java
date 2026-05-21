package bo.edu.univalle.sis.ue6dejunio_api.domain.models.student;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

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
    private String statusReason;
    private LocalDateTime createdAt;

    public String fullName() {
        return names + " " + lastNames;
    }
}
