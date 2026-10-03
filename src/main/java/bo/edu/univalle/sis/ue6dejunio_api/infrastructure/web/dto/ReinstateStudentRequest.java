package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * @param courseId the course of the active gestión to seat the student in. Optional while the
 *     student still holds a withdrawn seat this gestión, which is the one they return to; required
 *     otherwise
 */
public record ReinstateStudentRequest(@JsonProperty("id_course") UUID courseId) {}
