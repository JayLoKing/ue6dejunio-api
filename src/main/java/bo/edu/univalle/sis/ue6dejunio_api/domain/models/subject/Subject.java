package bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject;

import java.util.UUID;

public record Subject(UUID id, String name, String area, boolean active) {}
