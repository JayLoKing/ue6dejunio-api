package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.enrollment;

import java.util.UUID;

public interface IEnrollmentDomain {
    boolean existsEnrollment(UUID studentId, UUID classGroupId);
    void saveEnrollment(UUID studentId, UUID classGroupId);
}
