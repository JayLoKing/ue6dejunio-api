package bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.CreateStudentCommand;

import java.util.List;
import java.util.UUID;

public record EnrollToCourseCommand(
    UUID courseId,
    List<CreateStudentCommand> students
) {}
