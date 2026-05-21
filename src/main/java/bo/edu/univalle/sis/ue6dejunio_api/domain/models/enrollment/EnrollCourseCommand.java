package bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.CreateStudentCommand;

import java.util.List;

public record EnrollCourseCommand(
    Integer gradeId,
    Integer parallelId,
    List<CreateStudentCommand> students
) {}
