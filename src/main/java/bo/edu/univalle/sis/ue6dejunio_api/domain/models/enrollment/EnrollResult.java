package bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment;

public record EnrollResult(
    int studentsTotal,
    int studentsCreated,
    int studentsExisting,
    int classGroupsInCourse,
    int enrollmentsCreated,
    int enrollmentsSkipped
) {}
