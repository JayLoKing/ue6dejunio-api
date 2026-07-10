package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollResult;

public record EnrollResponse(
    int studentsTotal,
    int studentsCreated,
    int studentsExisting,
    int enrollmentsCreated,
    int enrollmentsSkipped
) {
    public static EnrollResponse from(EnrollResult r) {
        return new EnrollResponse(r.studentsTotal(), r.studentsCreated(), r.studentsExisting(),
            r.enrollmentsCreated(), r.enrollmentsSkipped());
    }
}
