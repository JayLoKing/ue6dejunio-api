package bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.CreateStudentCommand;
import java.util.List;
import java.util.UUID;

/**
 * @param actorId who is importing. An import can put a withdrawn student back on the roll, and that
 *     is a change of status like any other — it is recorded against whoever made it
 */
public record EnrollToCourseCommand(
        UUID courseId, List<CreateStudentCommand> students, UUID actorId) {}
