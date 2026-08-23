package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.security;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentEvent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentEventDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentScoreDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import org.springframework.security.core.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ownership-based authorization. Director reads and writes everything; Teacher only their own
 * class groups (subject) or their homeroom course, depending on the resource; Secretary reads
 * across the school and writes nothing.
 *
 * <p>Used from {@code @PreAuthorize}, e.g.
 * {@code @authz.canWriteClassGroup(authentication, #id)}.
 */
@Component("authz")
public class AuthorizationComponent {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationComponent.class);

    private static final String ROLE_DIRECTOR = "ROLE_Director";
    /**
     * School-wide read actor: reaches the academic record without owning a course, and matches no
     * write rule anywhere. Which reads exactly is decided by the GET rules in SecurityConfig — the
     * chain is the stricter layer, so a predicate granting more than the chain admits is simply
     * unreachable.
     */
    private static final String ROLE_SECRETARY = "ROLE_Secretary";
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
        UUID owner;
        try {
            owner = classGroupDomain.teacherIdOfClassGroup(classGroupId);
        } catch (ResourceNotFoundException ex) {
            // Same rule as ownsEnrollmentCourse: an id that resolves to nothing denies with 403
            // rather than surfacing as 404 from inside the guard.
            return false;
        }
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
        if (isReadOnlyStaff(authentication)) {
            return true;
        }
        return canWriteScoreEvent(authentication, eventId);
    }

    public boolean canReadEnrollmentScope(Authentication authentication, UUID courseEnrollmentId) {
        if (isReadOnlyStaff(authentication)) {
            return true;
        }
        return ownsEnrollmentCourse(authentication, courseEnrollmentId);
    }

    /**
     * The secretariat is a school-wide READ actor: it consults any course's records without owning
     * one. It is deliberately checked here, at the read entry points, and never inside
     * {@link #ownsEnrollmentCourse} — that helper also backs the write predicates, so widening it
     * would silently hand out write access.
     */
    private boolean isReadOnlyStaff(Authentication authentication) {
        return authentication != null && hasRole(authentication, ROLE_SECRETARY);
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
        UUID teacherId = userId(authentication);
        if (teacherId == null) {
            return false;
        }
        // A daily batch covers a whole roster, so resolving ownership one enrollment at a time
        // meant two queries per student inside @PreAuthorize. One query maps every enrollment to
        // its course; the homeroom check then runs over the distinct courses, which in practice is
        // one. Deliberately NOT homeroomCourseOf(teacherId): that returns a single course, so a
        // teacher holding two active homerooms would pass the per-student endpoint and be refused
        // by the batch for the very same rows.
        Map<UUID, UUID> courseByEnrollment =
            courseEnrollmentDomain.courseIdsByEnrollment(courseEnrollmentIds);
        if (!courseByEnrollment.keySet().containsAll(courseEnrollmentIds)) {
            return false;
        }
        for (UUID courseId : new HashSet<>(courseByEnrollment.values())) {
            Optional<Course> course = courseDomain.findById(courseId);
            if (course.isEmpty() || !teacherId.equals(course.get().homeroomTeacherId())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Scope of the student directory. Director and Secretary see whatever course was asked for
     * (or all of them when none is); a Teacher is always pinned to their own homeroom course and
     * never reaches another one.
     *
     * <p>Never denies with 403: a Teacher without a homeroom gets an id that matches nothing, so
     * the listing comes back empty instead of erroring.
     */
    public UUID effectiveDirectoryCourseId(Authentication authentication, UUID requestedCourseId) {
        if (authentication != null
            && (hasRole(authentication, ROLE_DIRECTOR) || isReadOnlyStaff(authentication))) {
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
        if (hasRole(authentication, ROLE_DIRECTOR) || isReadOnlyStaff(authentication)) {
            return true;
        }
        Optional<Course> course = courseDomain.findById(courseId);
        if (course.isEmpty()) {
            return false;
        }
        UUID userId = userId(authentication);
        return userId != null && userId.equals(course.get().homeroomTeacherId());
    }

    /**
     * Who may read a course's roster. Wider than {@link #canReadCourse} on purpose: that one is
     * homeroom-only, and a technical teacher has no homeroom — gating the roster on it would cut
     * them off from the very students they take attendance for.
     */
    public boolean canReadCourseRoster(Authentication authentication, UUID courseId) {
        if (authentication == null || courseId == null) {
            return false;
        }
        if (hasRole(authentication, ROLE_DIRECTOR) || isReadOnlyStaff(authentication)) {
            return true;
        }
        UUID teacherId = userId(authentication);
        if (teacherId == null) {
            return false;
        }
        Optional<Course> course = courseDomain.findById(courseId);
        if (course.isEmpty()) {
            return false;
        }
        return teacherId.equals(course.get().homeroomTeacherId())
            || classGroupDomain.teachesInCourse(teacherId, courseId);
    }

    /**
     * Who may read a single student. A teacher reaches only students enrolled in a course they
     * are tied to, either as homeroom teacher or through a class group they run.
     */
    public boolean canReadStudent(Authentication authentication, UUID studentId) {
        if (authentication == null || studentId == null) {
            return false;
        }
        if (hasRole(authentication, ROLE_DIRECTOR) || isReadOnlyStaff(authentication)) {
            return true;
        }
        for (UUID courseId : courseEnrollmentDomain.courseIdsOfStudent(studentId)) {
            if (canReadCourseRoster(authentication, courseId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Who may enroll students into a course. Narrower than {@link #canReadCourseRoster}: running a
     * subject in the course is enough to read its roster, but composing that roster is the
     * homeroom teacher's act, or the Director's.
     */
    public boolean canWriteCourseEnrollment(Authentication authentication, UUID courseId) {
        return canReadCourse(authentication, courseId) && !isReadOnlyStaff(authentication);
    }

    /**
     * Who may act on a student's record — withdrawal above all, which flips every effective
     * enrollment and therefore removes them from other teachers' rosters too. Only the Director or
     * the homeroom teacher of a course the student belongs to.
     */
    public boolean canWriteStudent(Authentication authentication, UUID studentId) {
        if (authentication == null || studentId == null || isReadOnlyStaff(authentication)) {
            return false;
        }
        if (hasRole(authentication, ROLE_DIRECTOR)) {
            return true;
        }
        for (UUID courseId : courseEnrollmentDomain.courseIdsOfStudent(studentId)) {
            if (canReadCourse(authentication, courseId)) {
                return true;
            }
        }
        return false;
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
        } catch (ResourceNotFoundException ex) {
            // An id that resolves to nothing is a denial, not an error. Anything else — a dropped
            // connection, say — must keep propagating instead of being reported as "not owner".
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

    /**
     * Caller id, or {@code null} when the token carries no usable subject. These predicates run
     * inside {@code @PreAuthorize}, so an exception here would surface as 500 instead of 403 —
     * a malformed subject must deny, not fail.
     */
    private UUID userId(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            String subject = jwt.getSubject();
            if (subject == null) {
                return null;
            }
            try {
                return UUID.fromString(subject);
            } catch (IllegalArgumentException ex) {
                log.debug("Rejecting token with non-UUID subject");
                return null;
            }
        }
        return null;
    }
}
