package bo.edu.univalle.sis.ue6dejunio_api.domain.models.student;

import java.util.UUID;

public record StudentDirectoryItem(
    UUID id,
    String rudeCode,
    String fullName,
    String grade,
    String parallel,
    String level
) {}
