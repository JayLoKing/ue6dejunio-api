package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.security;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentEvent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentEventDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentScoreDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * B10: autorizacion por propiedad. Director escribe/lee todo; Teacher solo sus class_groups
 * (materia) o su curso de aula (homeroom), segun el recurso.
 * Uso en @PreAuthorize: @authz.canWriteClassGroup(authentication, #id)
 */
@Component("authz")
public class AuthorizationComponent {

    private static final String ROLE_DIRECTOR = "ROLE_Director";
    private static final UUID NO_MATCH_COURSE_ID = new UUID(0L, 0L);

    private final IClassGroupDomain classGroupDomain;
    private final IAssessmentEventDomain assessmentEventDomain;
    private final IAssessmentScoreDomain assessmentScoreDomain;
    private final ICourseEnrollmentDomain courseEnrollmentDomain;
    private final ICourseDomain courseDomain;

    public AuthorizationComponent(
        IClassGroupDomain classGroupDomain,
        IAssessmentEventDomain assessmentEventDomain,
        IAssessmentScoreDomain assessmentScoreDomain,
        ICourseEnrollmentDomain courseEnrollmentDomain,
        ICourseDomain courseDomain
    ) {
        this.classGroupDomain = classGroupDomain;
        this.assessmentEventDomain = assessmentEventDomain;
        this.assessmentScoreDomain = assessmentScoreDomain;
        this.courseEnrollmentDomain = courseEnrollmentDomain;
        this.courseDomain = courseDomain;
    }

    public boolean canWriteClassGroup(Authentication authentication, UUID classGroupId) {
        if (authentication == null || classGroupId == null) {
            return false;
        }
        if (hasRole(authentication, ROLE_DIRECTOR)) {
            return true;
        }
        UUID userId = userId(authentication);
        UUID owner = classGroupDomain.teacherIdOfClassGroup(classGroupId);
        return userId != null && userId.equals(owner);
    }

    public boolean canWriteScoreEvent(Authentication authentication, UUID eventId) {
        if (authentication == null || eventId == null) {
            return false;
        }
        if (hasRole(authentication, ROLE_DIRECTOR)) {
            return true;
        }
        Optional<AssessmentEvent> event = assessmentEventDomain.findById(eventId);
        if (event.isEmpty() || event.get().classGroupId() == null) {
            return false;
        }
        return canWriteClassGroup(authentication, event.get().classGroupId());
    }

    public boolean canWriteScore(Authentication authentication, UUID scoreId) {
        if (authentication == null || scoreId == null) {
            return false;
        }
        if (hasRole(authentication, ROLE_DIRECTOR)) {
            return true;
        }
        Optional<AssessmentScore> score = assessmentScoreDomain.findById(scoreId);
        if (score.isEmpty()) {
            return false;
        }
        return canWriteScoreEvent(authentication, score.get().eventId());
    }

    public boolean canReadScoreEvent(Authentication authentication, UUID eventId) {
        return canWriteScoreEvent(authentication, eventId);
    }

    public boolean canReadEnrollmentScope(Authentication authentication, UUID courseEnrollmentId) {
        return ownsEnrollmentCourse(authentication, courseEnrollmentId);
    }

    public boolean canWriteDailyAttendance(Authentication authentication, UUID courseEnrollmentId) {
        return ownsEnrollmentCourse(authentication, courseEnrollmentId);
    }

    public boolean canWriteDailyBatch(Authentication authentication, Collection<UUID> courseEnrollmentIds) {
        if (authentication == null || courseEnrollmentIds == null || courseEnrollmentIds.isEmpty()) {
            return false;
        }
        if (hasRole(authentication, ROLE_DIRECTOR)) {
            return true;
        }
        for (UUID courseEnrollmentId : courseEnrollmentIds) {
            if (!ownsEnrollmentCourse(authentication, courseEnrollmentId)) {
                return false;
            }
        }
        return true;
    }

    /**
     * B31: alcance del directorio de estudiantes. Director ve todo (courseId solicitado o null).
     * Teacher siempre queda acotado a su propio curso de aula (homeroom); nunca a otro curso.
     * Nunca deniega (403): si el Teacher no tiene homeroom, retorna un UUID que no matchea nada.
     */
    public UUID effectiveDirectoryCourseId(Authentication authentication, UUID requestedCourseId) {
        if (authentication != null && hasRole(authentication, ROLE_DIRECTOR)) {
            return requestedCourseId;
        }
        UUID teacherId = authentication != null ? userId(authentication) : null;
        if (teacherId == null) {
            return NO_MATCH_COURSE_ID;
        }
        return courseDomain.homeroomCourseOf(teacherId)
            .map(Course::id)
            .orElse(NO_MATCH_COURSE_ID);
    }

    public boolean canReadCourse(Authentication authentication, UUID courseId) {
        if (authentication == null || courseId == null) {
            return false;
        }
        if (hasRole(authentication, ROLE_DIRECTOR)) {
            return true;
        }
        Optional<Course> course = courseDomain.findById(courseId);
        if (course.isEmpty()) {
            return false;
        }
        UUID userId = userId(authentication);
        return userId != null && userId.equals(course.get().homeroomTeacherId());
    }

    public boolean canReadTeacherRoster(Authentication authentication, UUID userId) {
        if (authentication == null || userId == null) {
            return false;
        }
        if (hasRole(authentication, ROLE_DIRECTOR)) {
            return true;
        }
        UUID callerId = userId(authentication);
        return callerId != null && callerId.equals(userId);
    }

    private boolean ownsEnrollmentCourse(Authentication authentication, UUID courseEnrollmentId) {
        if (authentication == null || courseEnrollmentId == null) {
            return false;
        }
        if (hasRole(authentication, ROLE_DIRECTOR)) {
            return true;
        }
        UUID courseId;
        try {
            courseId = courseEnrollmentDomain.courseOfEnrollment(courseEnrollmentId);
        } catch (RuntimeException ex) {
            return false;
        }
        if (courseId == null) {
            return false;
        }
        Optional<Course> course = courseDomain.findById(courseId);
        if (course.isEmpty()) {
            return false;
        }
        UUID userId = userId(authentication);
        return userId != null && userId.equals(course.get().homeroomTeacherId());
    }

    private boolean hasRole(Authentication auth, String role) {
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (role.equals(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private UUID userId(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            return UUID.fromString(jwt.getSubject());
        }
        return null;
    }
}
