package bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.CreateStudentCommand;
import java.util.List;
import java.util.UUID;

/**
 * @param actorId who runs the load. Recorded as the one who decided when the load reinstates a
 *     student withdrawn in an earlier gestión
 */
public record EnrollToCourseCommand(
        UUID courseId, List<CreateStudentCommand> students, UUID actorId) {}
