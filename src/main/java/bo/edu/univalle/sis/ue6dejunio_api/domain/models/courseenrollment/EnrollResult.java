package bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment;

public record EnrollResult(
    int studentsTotal,
    int studentsCreated,
    int studentsExisting,
    int enrollmentsCreated,
    int enrollmentsSkipped
) {}
