package bo.edu.univalle.sis.ue6dejunio_api.domain.models.student;

import java.util.UUID;

/**
 * The Director's decision to put a withdrawn student back on the roll.
 *
 * @param studentId who comes back
 * @param courseId the course of the active gestión to seat them in. Optional while they still hold
 *     a withdrawn enrolment this gestión, which is the seat they return to; required otherwise
 * @param actorId the Director who decided, recorded on the student's status
 */
public record ReinstateStudentCommand(UUID studentId, UUID courseId, UUID actorId) {}
