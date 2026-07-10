package bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog;

import java.util.UUID;

public record TeacherItem(UUID id, String fullName, String email, boolean technical) {}
