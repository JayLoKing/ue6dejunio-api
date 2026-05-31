package bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment;

import java.util.UUID;

public record EnrollmentRef(UUID enrollmentId, UUID studentId, String fullName) {}
