package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.security;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.Adaptation;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentEvent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.EvaluationCriterion;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.adaptation.IAdaptationDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentEventDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentScoreDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.criterion.ICriterionDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.pdc.IPdcDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskPredictionDomain;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Ownership-based authorization. Director reads and writes everything; Teacher only their own class
 * groups (subject) or their homeroom course, depending on the resource; Secretary reads across the
 * school and writes nothing.
 *
 * <p>Used from {@code @PreAuthorize}, e.g. {@code @authz.canWriteClassGroup(authentication, #id)}.
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
    private final ICriterionDomain criterionDomain;
    private final ICourseEnrollmentDomain courseEnrollmentDomain;
    private final ICourseDomain courseDomain;
    private final IPdcDomain pdcDomain;
    private final INotificationDomain notificationDomain;
    private final IAdaptationDomain adaptationDomain;
    private final IRiskPredictionDomain riskPredictionDomain;

    public AuthorizationComponent(
            IClassGroupDomain classGroupDomain,
            IAssessmentEventDomain assessmentEventDomain,
            IAssessmentScoreDomain assessmentScoreDomain,
            ICriterionDomain criterionDomain,
            ICourseEnrollmentDomain courseEnrollmentDomain,
            ICourseDomain courseDomain,
            IPdcDomain pdcDomain,
            INotificationDomain notificationDomain,
            IAdaptationDomain adaptationDomain,
            IRiskPredictionDomain riskPredictionDomain) {
        this.classGroupDomain = classGroupDomain;
        this.assessmentEventDomain = assessmentEventDomain;
        this.assessmentScoreDomain = assessmentScoreDomain;
        this.criterionDomain = criterionDomain;
        this.courseEnrollmentDomain = courseEnrollmentDomain;
        this.courseDomain = courseDomain;
        this.pdcDomain = pdcDomain;
        this.notificationDomain = notificationDomain;
        this.adaptationDomain = adaptationDomain;
        this.riskPredictionDomain = riskPredictionDomain;
    }

    /**
     * Ownership of a risk prediction, resolved through the subject it is about.
     *
     * <p>Role alone is not enough here even though the only writable field is a flag: the endpoint
     * answers with the prediction, so a teacher who guessed an id would read another course's
     * student, their level and their probability of failing. Enumerating ids would walk the risk
     * roster of the whole school.
     */
    public boolean canWriteRiskPrediction(Authentication authentication, UUID predictionId) {
        if (authentication == null || predictionId == null) {
            return false;
        }
        if (hasRole(authentication, ROLE_DIRECTOR)) {
            return true;
        }
        return riskPredictionDomain
                .findById(predictionId)
                .map(prediction -> canWriteClassGroup(authentication, prediction.classGroupId()))
                .orElse(false);
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
        } catch (ResourceNotFoundException ignored) {
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

    /**
     * Ownership of a criterion scored directly. It resolves through the criterion's own class
     * group, because such a score has no activity item to walk through.
     */
    public boolean canWriteScoreCriterion(Authentication authentication, UUID criterionId) {
        if (authentication == null || criterionId == null) {
            return false;
        }
        if (hasRole(authentication, ROLE_DIRECTOR)) {
            return true;
        }
        Optional<EvaluationCriterion> criterion = criterionDomain.findById(criterionId);
        if (criterion.isEmpty() || criterion.get().classGroupId() == null) {
            return false;
        }
        return canWriteClassGroup(authentication, criterion.get().classGroupId());
    }

    /**
     * Entry guard for the scoring endpoint, where the body carries one target or the other. A body
     * naming both, or neither, is denied here rather than reaching the service:
     * {@code @PreAuthorize} runs first, so an ambiguous target must not be allowed to pick a
     * branch.
     */
    public boolean canWriteScoreTarget(
            Authentication authentication, UUID eventId, UUID criterionId) {
        if ((eventId == null) == (criterionId == null)) {
            return false;
        }
        return eventId != null
                ? canWriteScoreEvent(authentication, eventId)
                : canWriteScoreCriterion(authentication, criterionId);
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
        return canWriteScoreTarget(
                authentication, score.get().eventId(), score.get().criterionId());
    }

    public boolean canReadScoreEvent(Authentication authentication, UUID eventId) {
        if (isReadOnlyStaff(authentication)) {
            return true;
        }
        if (authentication == null || eventId == null) {
            return false;
        }
        if (hasRole(authentication, ROLE_DIRECTOR)) {
            return true;
        }
        return assessmentEventDomain
                .findById(eventId)
                .map(event -> canReadClassGroup(authentication, event.classGroupId()))
                .orElse(false);
    }

    public boolean canReadScoreCriterion(Authentication authentication, UUID criterionId) {
        if (isReadOnlyStaff(authentication)) {
            return true;
        }
        if (authentication == null || criterionId == null) {
            return false;
        }
        if (hasRole(authentication, ROLE_DIRECTOR)) {
            return true;
        }
        return criterionDomain
                .findById(criterionId)
                .map(criterion -> canReadClassGroup(authentication, criterion.classGroupId()))
                .orElse(false);
    }

    /**
     * Who may read a class group's criteria and marks. Deliberately wider than {@link
     * #canWriteClassGroup}: two different teachers reach the same subject, and only one of them may
     * write it.
     *
     * <ul>
     *   <li>The teacher the Director put in charge of the class group — they record its marks, so
     *       reading them is the smaller half of what they already do.
     *   <li>The homeroom teacher of the course it belongs to. The technical subjects — Música,
     *       Religión, Técnica Tecnológica — are run by a technical teacher, but the homeroom
     *       teacher answers for that classroom as a whole and signs the libreta, the centralizador
     *       and the informe pedagógico, every one of which quotes these very marks. Gating this on
     *       ownership denied them their own course's records with a 403.
     * </ul>
     *
     * <p>When the Director assigns a technical subject to the homeroom teacher themselves, the
     * first case already covers them and they write it like any other.
     *
     * <p>Resolved through {@code findById} rather than {@code teacherIdOfClassGroup} so that one
     * lookup answers both questions; the course is only asked for when the caller turns out not to
     * be the subject's own teacher.
     */
    public boolean canReadClassGroup(Authentication authentication, UUID classGroupId) {
        if (isReadOnlyStaff(authentication)) {
            return true;
        }
        if (authentication == null || classGroupId == null) {
            return false;
        }
        if (hasRole(authentication, ROLE_DIRECTOR)) {
            return true;
        }
        UUID userId = userId(authentication);
        if (userId == null) {
            return false;
        }
        return classGroupDomain
                .findById(classGroupId)
                .map(
                        cg ->
                                userId.equals(cg.teacherId())
                                        || isHomeroomTeacherOf(userId, cg.courseId()))
                .orElse(false);
    }

    /**
     * Whether this teacher is the one in charge of that course. A course between homeroom teachers
     * carries a null there, so the comparison is made from the caller's id and never the other way
     * round — a null on either side denies.
     */
    private boolean isHomeroomTeacherOf(UUID teacherId, UUID courseId) {
        if (teacherId == null || courseId == null) {
            return false;
        }
        return courseDomain
                .findById(courseId)
                .map(course -> teacherId.equals(course.homeroomTeacherId()))
                .orElse(false);
    }

    public boolean canReadEnrollmentScope(Authentication authentication, UUID courseEnrollmentId) {
        if (isReadOnlyStaff(authentication)) {
            return true;
        }
        return ownsEnrollmentCourse(authentication, courseEnrollmentId);
    }

    /**
     * The secretariat is a school-wide READ actor: it consults any course's records without owning
     * one. It is deliberately checked here, at the read entry points, and never inside {@link
     * #ownsEnrollmentCourse} — that helper also backs the write predicates, so widening it would
     * silently hand out write access.
     */
    private boolean isReadOnlyStaff(Authentication authentication) {
        return authentication != null && hasRole(authentication, ROLE_SECRETARY);
    }

    public boolean canWriteDailyAttendance(Authentication authentication, UUID courseEnrollmentId) {
        return ownsEnrollmentCourse(authentication, courseEnrollmentId);
    }

    public boolean canWriteDailyBatch(
            Authentication authentication, Collection<UUID> courseEnrollmentIds) {
        if (authentication == null
                || courseEnrollmentIds == null
                || courseEnrollmentIds.isEmpty()) {
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
        return courseDomain.isHomeroomTeacherOfAll(
                teacherId, new HashSet<>(courseByEnrollment.values()));
    }

    /**
     * Scope of the student directory. Director and Secretary see whatever course was asked for (or
     * all of them when none is); a Teacher is always pinned to their own homeroom course and never
     * reaches another one.
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
        return courseDomain.homeroomCourseOf(teacherId).map(Course::id).orElse(NO_MATCH_COURSE_ID);
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
     * Who may read a single student. A teacher reaches only students enrolled in a course they are
     * tied to, either as homeroom teacher or through a class group they run.
     *
     * <p>The tie is asked once for every course at a time. Walking the courses and calling {@link
     * #canReadCourseRoster} per course made the guard cost grow with the student's enrollments, and
     * this runs before every read of the record.
     */
    public boolean canReadStudent(Authentication authentication, UUID studentId) {
        if (authentication == null || studentId == null) {
            return false;
        }
        if (hasRole(authentication, ROLE_DIRECTOR) || isReadOnlyStaff(authentication)) {
            return true;
        }
        UUID teacherId = userId(authentication);
        if (teacherId == null) {
            return false;
        }
        List<UUID> courseIds = courseEnrollmentDomain.courseIdsOfStudent(studentId);
        if (courseIds.isEmpty()) {
            return false;
        }
        return courseDomain.isHomeroomTeacherOfAny(teacherId, courseIds)
                || classGroupDomain.teachesInAnyCourse(teacherId, courseIds);
    }

    /**
     * Who may enroll students into a course. Narrower than {@link #canReadCourseRoster}: running a
     * subject in the course is enough to read its roster, but composing that roster is the homeroom
     * teacher's act, or the Director's.
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
        UUID teacherId = userId(authentication);
        if (teacherId == null) {
            return false;
        }
        List<UUID> courseIds = courseEnrollmentDomain.courseIdsOfStudent(studentId);
        if (courseIds.isEmpty()) {
            return false;
        }
        // Homeroom only, on purpose: running a subject in the course is not enough to withdraw
        // a student out of everyone else's roster. Same rule canReadCourse applies per course.
        return courseDomain.isHomeroomTeacherOfAny(teacherId, courseIds);
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

    /**
     * Who may open a month's plan for a course. The homeroom teacher writes the course-wide plan,
     * and a specialist writes one for the subject they run there — so running any class group of
     * the course is enough. Narrower guards do not fit: {@link #canReadCourse} is homeroom-only and
     * would cut the specialist off from planning the subject they actually teach.
     */
    public boolean canWritePdcForCourse(Authentication authentication, UUID courseId) {
        if (authentication == null || courseId == null || isReadOnlyStaff(authentication)) {
            return false;
        }
        // The Director opens no plans. A month is planned by whoever delivers it, and the office
        // reviews what comes back — a plan opened from the office would carry no teaching behind
        // it.
        UUID teacherId = userId(authentication);
        if (teacherId == null) {
            return false;
        }
        List<UUID> courseIds = List.of(courseId);
        return courseDomain.isHomeroomTeacherOfAny(teacherId, courseIds)
                || classGroupDomain.teachesInAnyCourse(teacherId, courseIds);
    }

    /**
     * Who may write a course's informe pedagógico. Its homeroom teacher, and nobody else.
     *
     * <p>Narrower than {@link #canWritePdcForCourse} on purpose: a specialist plans the subject
     * they run, but the informe is a statement about the whole classroom, and the school's own form
     * carries one DOCENTE on it — the homeroom teacher, who closes it with "esto es lo que puedo
     * dar fe" over their signature. A subject teacher writing inside it would put their account of
     * one area under somebody else's name.
     *
     * <p>The Director is not here either, for the same reason they open no plans: the office reads
     * what the classroom hands in. Reading it is {@link #canReadCourse}, which the Director and the
     * secretariat already pass — this guard is only about who signs.
     */
    public boolean canWritePedagogicalReport(Authentication authentication, UUID courseId) {
        if (authentication == null || courseId == null || isReadOnlyStaff(authentication)) {
            return false;
        }
        UUID teacherId = userId(authentication);
        if (teacherId == null) {
            return false;
        }
        return courseDomain.isHomeroomTeacherOfAny(teacherId, List.of(courseId));
    }

    /**
     * Ownership of a curricular plan. A PDC belongs to a course and covers several subjects, so a
     * teacher has a stake in it either by running the course or by teaching one of its subjects — a
     * specialist has to reach the plan to write their own block. Read-only staff writes nowhere.
     *
     * <p>The Director is not here. A plan is written by the teachers who deliver it; the Director
     * reads what they publish and answers with an approval or an observation. Letting the office
     * write inside the document would put its content under a name that never taught the class.
     * What the Director may still do to the plan as a whole is settled by {@link
     * #canAdministerPdc}.
     *
     * <p>Reaching the plan is not the same as writing any part of it: which block a teacher may
     * rewrite is settled by {@link #canWritePdcSubject}.
     */
    public boolean canWritePdc(Authentication authentication, UUID pdcId) {
        if (authentication == null || pdcId == null || isReadOnlyStaff(authentication)) {
            return false;
        }
        UUID callerId = userId(authentication);
        // Asks for the writers' ids rather than the plan: this runs before every write, and reading
        // the document to compare a handful of UUIDs pulled every block and every weekly row with
        // it.
        return callerId != null && pdcDomain.writerIdsOf(pdcId).contains(callerId);
    }

    /**
     * Who may dispose of the plan as a document: correct its heading or remove it. Narrower than
     * {@link #canWritePdc}, which only says who may reach the plan — a specialist holding one block
     * of a course-wide plan writes their subject, but the document as a whole is the author's, or
     * the homeroom teacher's.
     *
     * <p>The Director keeps this one. A plan opened against the wrong course, or left behind by a
     * teacher who has since gone, has nobody else who can correct or remove it — the author is
     * exactly who is missing in those cases.
     */
    public boolean canAdministerPdc(Authentication authentication, UUID pdcId) {
        if (authentication == null || pdcId == null || isReadOnlyStaff(authentication)) {
            return false;
        }
        if (hasRole(authentication, ROLE_DIRECTOR)) {
            return true;
        }
        UUID callerId = userId(authentication);
        return callerId != null && pdcDomain.administratorIdsOf(pdcId).contains(callerId);
    }

    /**
     * Who may move the plan through the teachers' half of its life: hand it to the other parallels,
     * and publish it for review.
     *
     * <p>The same people as {@link #canAdministerPdc} minus the Director. Publishing is a teacher
     * saying their month is ready, and the rotation is a teacher handing their work to the
     * parallels; a Director doing either would be reviewing a submission they made themselves.
     */
    public boolean canAuthorPdc(Authentication authentication, UUID pdcId) {
        if (authentication == null || pdcId == null) {
            return false;
        }
        if (hasRole(authentication, ROLE_DIRECTOR)) {
            return false;
        }
        return canAdministerPdc(authentication, pdcId);
    }

    /**
     * Which subject block of a plan a teacher may rewrite: the homeroom teacher owns the whole
     * plan, a specialist owns only the block for the subject they teach. Without this a teacher
     * with one block could rewrite every other subject of the course.
     */
    public boolean canWritePdcSubject(
            Authentication authentication, UUID pdcId, UUID planSubjectId) {
        if (authentication == null
                || pdcId == null
                || planSubjectId == null
                || isReadOnlyStaff(authentication)) {
            return false;
        }
        // No Director branch, for the reason given on canWritePdc: the block is the teacher's work.
        UUID callerId = userId(authentication);
        // Matched inside this plan: a block id from another plan contributes nobody to the set,
        // so it resolves to a denial rather than to someone else's block.
        return callerId != null
                && pdcDomain.subjectWriterIdsOf(pdcId, planSubjectId).contains(callerId);
    }

    /**
     * Read side of {@link #canWritePdc}: the secretariat reads, a Teacher stays in their own plans.
     *
     * <p>The Director reads every plan, which the write guard no longer grants them. Reviewing is
     * the whole of their part in this: they cannot open a plan, write in it or publish it, and
     * without this branch they could not read the one they are asked to approve either.
     */
    public boolean canReadPdc(Authentication authentication, UUID pdcId) {
        if (authentication == null || pdcId == null) {
            return false;
        }
        if (isReadOnlyStaff(authentication) || hasRole(authentication, ROLE_DIRECTOR)) {
            return true;
        }
        return canWritePdc(authentication, pdcId);
    }

    /**
     * Which plans a listing may span. The PDC list carries no mandatory class group filter, so
     * without a scope a Teacher would page through the whole school's plans. Director and the
     * secretariat legitimately see everything, which is what {@code null} means here; a Teacher is
     * narrowed to their own. A token with no usable subject resolves to an id no plan can carry,
     * because widening on a broken token is exactly the failure this closes.
     */
    public UUID pdcListScopeTeacherId(Authentication authentication) {
        if (authentication == null) {
            return NO_MATCH_COURSE_ID;
        }
        if (hasRole(authentication, ROLE_DIRECTOR) || isReadOnlyStaff(authentication)) {
            return null;
        }
        UUID teacherId = userId(authentication);
        return teacherId != null ? teacherId : NO_MATCH_COURSE_ID;
    }

    /**
     * Ownership of a curricular adaptation, resolved through the plan it hangs off. An adaptation
     * names the student it was written for, so reading one is disclosure, not just metadata.
     */
    public boolean canWriteAdaptation(Authentication authentication, UUID adaptationId) {
        if (authentication == null || adaptationId == null || isReadOnlyStaff(authentication)) {
            return false;
        }
        if (hasRole(authentication, ROLE_DIRECTOR)) {
            return true;
        }
        Optional<Adaptation> adaptation = adaptationDomain.findById(adaptationId);
        if (adaptation.isEmpty() || adaptation.get().planId() == null) {
            return false;
        }
        return canWritePdc(authentication, adaptation.get().planId());
    }

    /** Read side of {@link #canWriteAdaptation}. */
    public boolean canReadAdaptation(Authentication authentication, UUID adaptationId) {
        if (isReadOnlyStaff(authentication)) {
            return true;
        }
        return canWriteAdaptation(authentication, adaptationId);
    }

    /**
     * A notification is personal: only its receiver may mark it read or delete it. Not even the
     * Director bypasses this one — acting on someone else's inbox is not an act of authority.
     */
    public boolean canActOnNotification(Authentication authentication, UUID notificationId) {
        if (authentication == null || notificationId == null) {
            return false;
        }
        UUID callerId = userId(authentication);
        if (callerId == null) {
            return false;
        }
        return notificationDomain
                .findById(notificationId)
                .map(n -> callerId.equals(n.receiverId()))
                .orElse(false);
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
        } catch (ResourceNotFoundException ignored) {
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
        // An absent authentication holds no role. Every caller guards first, but reaching here with
        // null used to throw inside @PreAuthorize, which Spring surfaces as a 500 — an
        // unauthenticated
        // request has to be denied, not turned into a server error.
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (role.equals(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Caller id, or {@code null} when the token carries no usable subject. These predicates run
     * inside {@code @PreAuthorize}, so an exception here would surface as 500 instead of 403 — a
     * malformed subject must deny, not fail.
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
            } catch (IllegalArgumentException ignored) {
                log.debug("Rejecting token with non-UUID subject");
                return null;
            }
        }
        return null;
    }
}
