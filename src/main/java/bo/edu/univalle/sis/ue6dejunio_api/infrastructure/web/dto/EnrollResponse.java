package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment.EnrollResult;

public record EnrollResponse(
    int studentsTotal,
    int studentsCreated,
    int studentsExisting,
    int classGroupsInCourse,
    int enrollmentsCreated,
    int enrollmentsSkipped
) {
    public static EnrollResponse from(EnrollResult r) {
        return new EnrollResponse(
            r.studentsTotal(), r.studentsCreated(), r.studentsExisting(),
            r.classGroupsInCourse(), r.enrollmentsCreated(), r.enrollmentsSkipped()
        );
    }
}
