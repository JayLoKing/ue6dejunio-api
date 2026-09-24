package bo.edu.univalle.sis.ue6dejunio_api.domain.models.student;

import java.time.LocalDate;

public record CreateStudentCommand(
        String rudeCode,
        String identityCard,
        String names,
        String lastNames,
        LocalDate birthDate,
        String gender) {}
