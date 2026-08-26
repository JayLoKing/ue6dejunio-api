package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.security;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.Adaptation;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.Pdc;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.adaptation.IAdaptationDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.Notification;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.pdc.IPdcDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentEvent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.EvaluationCriterion;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentEventDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentScoreDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.criterion.ICriterionDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationComponentTest {

    @Mock private IClassGroupDomain classGroupDomain;
    @Mock private IAssessmentEventDomain assessmentEventDomain;
    @Mock private IAssessmentScoreDomain assessmentScoreDomain;
    @Mock private ICriterionDomain criterionDomain;
    @Mock private ICourseEnrollmentDomain courseEnrollmentDomain;
    @Mock private ICourseDomain courseDomain;
    @Mock private IPdcDomain pdcDomain;
    @Mock private IAdaptationDomain adaptationDomain;
    @Mock private INotificationDomain notificationDomain;

    @InjectMocks private AuthorizationComponent authz;

    private JwtAuthenticationToken token(UUID userId, String role) {
        List<GrantedAuthority> authorities = role == null
            ? List.of()
            : List.of(new SimpleGrantedAuthority("ROLE_" + role));
        Jwt jwt = Jwt.withTokenValue("t")
            .header("alg", "RS256")
            .subject(userId.toString())
            .claim("role", role)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
        return new JwtAuthenticationToken(jwt, authorities, userId.toString());
    }

    // ---- canWriteScoreEvent ----

    @Test
    void canWriteScoreEvent_owner_true() {
        UUID teacherA = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID classGroupId = UUID.randomUUID();
        when(assessmentEventDomain.findById(eventId)).thenReturn(
            Optional.of(new AssessmentEvent(eventId, UUID.randomUUID(), classGroupId, 1, "Knowing", "t")));
        when(classGroupDomain.teacherIdOfClassGroup(classGroupId)).thenReturn(teacherA);

        assertThat(authz.canWriteScoreEvent(token(teacherA, "Teacher"), eventId)).isTrue();
    }

    @Test
    void canWriteScoreEvent_nonOwner_false() {
        UUID teacherA = UUID.randomUUID();
        UUID teacherB = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID classGroupId = UUID.randomUUID();
        when(assessmentEventDomain.findById(eventId)).thenReturn(
            Optional.of(new AssessmentEvent(eventId, UUID.randomUUID(), classGroupId, 1, "Knowing", "t")));
        when(classGroupDomain.teacherIdOfClassGroup(classGroupId)).thenReturn(teacherA);

        assertThat(authz.canWriteScoreEvent(token(teacherB, "Teacher"), eventId)).isFalse();
    }

    @Test
    void canWriteScoreEvent_director_bypassesWithoutLookup() {
        UUID director = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        assertThat(authz.canWriteScoreEvent(token(director, "Director"), eventId)).isTrue();
    }

    @Test
    void canWriteScoreEvent_unknownEventId_deniesNotThrows() {
        UUID teacherA = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(assessmentEventDomain.findById(eventId)).thenReturn(Optional.empty());

        assertThat(authz.canWriteScoreEvent(token(teacherA, "Teacher"), eventId)).isFalse();
    }

    // ---- canWriteScore (delete) ----

    @Test
    void canWriteScore_director_bypass() {
        UUID director = UUID.randomUUID();
        UUID scoreId = UUID.randomUUID();

        assertThat(authz.canWriteScore(token(director, "Director"), scoreId)).isTrue();
    }

    @Test
    void canWriteScore_owner_true_nonOwner_false() {
        UUID teacherA = UUID.randomUUID();
        UUID teacherB = UUID.randomUUID();
        UUID scoreId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID classGroupId = UUID.randomUUID();
        when(assessmentScoreDomain.findById(scoreId)).thenReturn(
            Optional.of(new AssessmentScore(scoreId, UUID.randomUUID(), eventId, null, BigDecimal.TEN, null, null)));
        when(assessmentEventDomain.findById(eventId)).thenReturn(
            Optional.of(new AssessmentEvent(eventId, UUID.randomUUID(), classGroupId, 1, "Knowing", "t")));
        when(classGroupDomain.teacherIdOfClassGroup(classGroupId)).thenReturn(teacherA);

        assertThat(authz.canWriteScore(token(teacherA, "Teacher"), scoreId)).isTrue();
        assertThat(authz.canWriteScore(token(teacherB, "Teacher"), scoreId)).isFalse();
    }

    @Test
    void canWriteScore_unknownScoreId_deniesNotThrows() {
        UUID teacherA = UUID.randomUUID();
        UUID scoreId = UUID.randomUUID();
        when(assessmentScoreDomain.findById(scoreId)).thenReturn(Optional.empty());

        assertThat(authz.canWriteScore(token(teacherA, "Teacher"), scoreId)).isFalse();
    }

    // ---- canWriteScoreCriterion / canWriteScoreTarget (direct criterion scoring) ----

    private EvaluationCriterion criterion(UUID id, UUID classGroupId) {
        return new EvaluationCriterion(id, classGroupId, 1, "Doing", "Participacion", null, null);
    }

    @Test
    void canWriteScoreCriterion_ownerOfTheClassGroup_true() {
        UUID teacherA = UUID.randomUUID();
        UUID criterionId = UUID.randomUUID();
        UUID classGroupId = UUID.randomUUID();
        when(criterionDomain.findById(criterionId))
            .thenReturn(Optional.of(criterion(criterionId, classGroupId)));
        when(classGroupDomain.teacherIdOfClassGroup(classGroupId)).thenReturn(teacherA);

        assertThat(authz.canWriteScoreCriterion(token(teacherA, "Teacher"), criterionId)).isTrue();
    }

    @Test
    void canWriteScoreCriterion_nonOwner_false() {
        UUID teacherA = UUID.randomUUID();
        UUID teacherB = UUID.randomUUID();
        UUID criterionId = UUID.randomUUID();
        UUID classGroupId = UUID.randomUUID();
        when(criterionDomain.findById(criterionId))
            .thenReturn(Optional.of(criterion(criterionId, classGroupId)));
        when(classGroupDomain.teacherIdOfClassGroup(classGroupId)).thenReturn(teacherA);

        assertThat(authz.canWriteScoreCriterion(token(teacherB, "Teacher"), criterionId)).isFalse();
    }

    @Test
    void canWriteScoreCriterion_unknownCriterionId_deniesNotThrows() {
        UUID teacherA = UUID.randomUUID();
        UUID criterionId = UUID.randomUUID();
        when(criterionDomain.findById(criterionId)).thenReturn(Optional.empty());

        assertThat(authz.canWriteScoreCriterion(token(teacherA, "Teacher"), criterionId)).isFalse();
    }

    @Test
    void canWriteScoreTarget_bothTargets_deniesEvenForDirector() {
        UUID director = UUID.randomUUID();

        assertThat(authz.canWriteScoreTarget(
            token(director, "Director"), UUID.randomUUID(), UUID.randomUUID())).isFalse();
    }

    @Test
    void canWriteScoreTarget_noTarget_denies() {
        UUID director = UUID.randomUUID();

        assertThat(authz.canWriteScoreTarget(token(director, "Director"), null, null)).isFalse();
    }

    @Test
    void canWriteScoreTarget_criterionOnly_resolvesThroughTheCriterion() {
        UUID teacherA = UUID.randomUUID();
        UUID criterionId = UUID.randomUUID();
        UUID classGroupId = UUID.randomUUID();
        when(criterionDomain.findById(criterionId))
            .thenReturn(Optional.of(criterion(criterionId, classGroupId)));
        when(classGroupDomain.teacherIdOfClassGroup(classGroupId)).thenReturn(teacherA);

        assertThat(authz.canWriteScoreTarget(token(teacherA, "Teacher"), null, criterionId)).isTrue();
    }

    // ---- canReadEnrollmentScope / canWriteDailyAttendance (homeroom) ----

    @Test
    void canReadEnrollmentScope_homeroomOwner_true() {
        UUID homeroomTeacher = UUID.randomUUID();
        UUID ceId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(courseEnrollmentDomain.courseOfEnrollment(ceId)).thenReturn(courseId);
        when(courseDomain.findById(courseId)).thenReturn(
            Optional.of(new Course(courseId, 1, "Primero", 1, "A", 1, 2026, homeroomTeacher, "Ana", true)));

        assertThat(authz.canReadEnrollmentScope(token(homeroomTeacher, "Teacher"), ceId)).isTrue();
    }

    @Test
    void canReadEnrollmentScope_nonHomeroomTeacher_false() {
        UUID homeroomTeacher = UUID.randomUUID();
        UUID otherTeacher = UUID.randomUUID();
        UUID ceId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(courseEnrollmentDomain.courseOfEnrollment(ceId)).thenReturn(courseId);
        when(courseDomain.findById(courseId)).thenReturn(
            Optional.of(new Course(courseId, 1, "Primero", 1, "A", 1, 2026, homeroomTeacher, "Ana", true)));

        assertThat(authz.canReadEnrollmentScope(token(otherTeacher, "Teacher"), ceId)).isFalse();
    }

    @Test
    void canWriteDailyAttendance_director_bypassesWithoutLookup() {
        UUID director = UUID.randomUUID();
        UUID ceId = UUID.randomUUID();

        assertThat(authz.canWriteDailyAttendance(token(director, "Director"), ceId)).isTrue();
    }

    @Test
    void canWriteDailyAttendance_unknownEnrollment_deniesNotThrows() {
        UUID teacherA = UUID.randomUUID();
        UUID ceId = UUID.randomUUID();
        when(courseEnrollmentDomain.courseOfEnrollment(ceId))
            .thenThrow(new bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException("CourseEnrollment", ceId));

        assertThat(authz.canWriteDailyAttendance(token(teacherA, "Teacher"), ceId)).isFalse();
    }

    // ---- canWriteDailyBatch (atomicity) ----

    @Test
    void canWriteDailyBatch_allOwned_true() {
        UUID homeroomTeacher = UUID.randomUUID();
        UUID ce1 = UUID.randomUUID();
        UUID ce2 = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(courseEnrollmentDomain.courseIdsByEnrollment(List.of(ce1, ce2)))
            .thenReturn(Map.of(ce1, courseId, ce2, courseId));
        when(courseDomain.isHomeroomTeacherOfAll(homeroomTeacher, Set.of(courseId))).thenReturn(true);

        assertThat(authz.canWriteDailyBatch(token(homeroomTeacher, "Teacher"), List.of(ce1, ce2))).isTrue();
    }

    @Test
    void canWriteDailyBatch_teacherWithTwoHomerooms_allowsBoth() {
        UUID teacher = UUID.randomUUID();
        UUID ce1 = UUID.randomUUID();
        UUID ce2 = UUID.randomUUID();
        UUID courseA = UUID.randomUUID();
        UUID courseB = UUID.randomUUID();
        when(courseEnrollmentDomain.courseIdsByEnrollment(List.of(ce1, ce2)))
            .thenReturn(Map.of(ce1, courseA, ce2, courseB));
        when(courseDomain.isHomeroomTeacherOfAll(teacher, Set.of(courseA, courseB))).thenReturn(true);

        // The per-student endpoint accepts any course the caller is homeroom of, so the batch has
        // to agree: same actor, same rows, same answer.
        assertThat(authz.canWriteDailyBatch(token(teacher, "Teacher"), List.of(ce1, ce2))).isTrue();
    }

    @Test
    void canWriteDailyBatch_unknownEnrollment_denies() {
        UUID teacher = UUID.randomUUID();
        UUID known = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        when(courseEnrollmentDomain.courseIdsByEnrollment(List.of(known, unknown)))
            .thenReturn(Map.of(known, UUID.randomUUID()));

        assertThat(authz.canWriteDailyBatch(token(teacher, "Teacher"), List.of(known, unknown))).isFalse();
    }

    @Test
    void canWriteDailyBatch_mixedOwnership_deniesWhole() {
        UUID homeroomTeacherA = UUID.randomUUID();
        UUID ceOwned = UUID.randomUUID();
        UUID ceForeign = UUID.randomUUID();
        UUID courseA = UUID.randomUUID();
        UUID courseB = UUID.randomUUID();
        when(courseEnrollmentDomain.courseIdsByEnrollment(List.of(ceOwned, ceForeign)))
            .thenReturn(Map.of(ceOwned, courseA, ceForeign, courseB));
        // One course is the caller's, the other is not: owning part of the batch owns none of it.
        when(courseDomain.isHomeroomTeacherOfAll(homeroomTeacherA, Set.of(courseA, courseB))).thenReturn(false);

        assertThat(authz.canWriteDailyBatch(token(homeroomTeacherA, "Teacher"), List.of(ceOwned, ceForeign))).isFalse();
    }

    @Test
    void canWriteDailyBatch_courseWithoutHomeroomTeacher_denies() {
        UUID teacher = UUID.randomUUID();
        UUID ce = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(courseEnrollmentDomain.courseIdsByEnrollment(List.of(ce)))
            .thenReturn(Map.of(ce, courseId));
        when(courseDomain.isHomeroomTeacherOfAll(teacher, Set.of(courseId))).thenReturn(false);

        assertThat(authz.canWriteDailyBatch(token(teacher, "Teacher"), List.of(ce))).isFalse();
    }

    // ---- canReadCourseRoster / canReadStudent ----

    @Test
    void canReadCourseRoster_technicalTeacherOfTheCourse_true() {
        UUID technicalTeacher = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(courseDomain.findById(courseId)).thenReturn(
            Optional.of(new Course(courseId, 1, "Primero", 1, "A", 1, 2026, UUID.randomUUID(), "Ana", true)));
        when(classGroupDomain.teachesInCourse(technicalTeacher, courseId)).thenReturn(true);

        // A technical teacher has no homeroom; gating the roster on homeroom alone would cut them
        // off from the students they take attendance for.
        assertThat(authz.canReadCourseRoster(token(technicalTeacher, "Teacher"), courseId)).isTrue();
    }

    @Test
    void canReadCourseRoster_homeroomTeacher_true() {
        UUID homeroomTeacher = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(courseDomain.findById(courseId)).thenReturn(
            Optional.of(new Course(courseId, 1, "Primero", 1, "A", 1, 2026, homeroomTeacher, "Ana", true)));

        assertThat(authz.canReadCourseRoster(token(homeroomTeacher, "Teacher"), courseId)).isTrue();
    }

    @Test
    void canReadCourseRoster_unrelatedTeacher_false() {
        UUID otherTeacher = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(courseDomain.findById(courseId)).thenReturn(
            Optional.of(new Course(courseId, 1, "Primero", 1, "A", 1, 2026, UUID.randomUUID(), "Ana", true)));
        when(classGroupDomain.teachesInCourse(otherTeacher, courseId)).thenReturn(false);

        assertThat(authz.canReadCourseRoster(token(otherTeacher, "Teacher"), courseId)).isFalse();
    }

    @Test
    void canReadCourseRoster_secretary_true() {
        assertThat(authz.canReadCourseRoster(
            token(UUID.randomUUID(), "Secretary"), UUID.randomUUID())).isTrue();
    }

    @Test
    void canReadStudent_homeroomTeacherOfOneOfTheStudentCourses_true() {
        UUID teacher = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        List<UUID> courses = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(courseEnrollmentDomain.courseIdsOfStudent(studentId)).thenReturn(courses);
        when(courseDomain.isHomeroomTeacherOfAny(teacher, courses)).thenReturn(true);

        assertThat(authz.canReadStudent(token(teacher, "Teacher"), studentId)).isTrue();
    }

    @Test
    void canReadStudent_technicalTeacherRunningAClassGroupInOneOfTheCourses_true() {
        UUID technicalTeacher = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        List<UUID> courses = List.of(UUID.randomUUID());
        when(courseEnrollmentDomain.courseIdsOfStudent(studentId)).thenReturn(courses);
        when(courseDomain.isHomeroomTeacherOfAny(technicalTeacher, courses)).thenReturn(false);
        when(classGroupDomain.teachesInAnyCourse(technicalTeacher, courses)).thenReturn(true);

        // Sin homeroom, la clase que dicta es lo unico que lo ata al estudiante.
        assertThat(authz.canReadStudent(token(technicalTeacher, "Teacher"), studentId)).isTrue();
    }

    @Test
    void canReadStudent_studentOutsideEveryCourseOfTheTeacher_false() {
        UUID teacher = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        List<UUID> courses = List.of(UUID.randomUUID());
        when(courseEnrollmentDomain.courseIdsOfStudent(studentId)).thenReturn(courses);
        when(courseDomain.isHomeroomTeacherOfAny(teacher, courses)).thenReturn(false);
        when(classGroupDomain.teachesInAnyCourse(teacher, courses)).thenReturn(false);

        assertThat(authz.canReadStudent(token(teacher, "Teacher"), studentId)).isFalse();
    }

    @Test
    void canReadStudent_studentWithoutEnrollments_deniesWithoutAskingTheStore() {
        UUID teacher = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(courseEnrollmentDomain.courseIdsOfStudent(studentId)).thenReturn(List.of());

        assertThat(authz.canReadStudent(token(teacher, "Teacher"), studentId)).isFalse();
        verify(courseDomain, never()).isHomeroomTeacherOfAny(any(), any());
        verify(classGroupDomain, never()).teachesInAnyCourse(any(), any());
    }

    @Test
    void canReadStudent_asksTheStoreOnce_noMatterHowManyCoursesTheStudentIsIn() {
        UUID teacher = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        List<UUID> courses = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(courseEnrollmentDomain.courseIdsOfStudent(studentId)).thenReturn(courses);
        when(courseDomain.isHomeroomTeacherOfAny(teacher, courses)).thenReturn(false);
        when(classGroupDomain.teachesInAnyCourse(teacher, courses)).thenReturn(true);

        assertThat(authz.canReadStudent(token(teacher, "Teacher"), studentId)).isTrue();

        // El chequeo se resuelve en dos consultas fijas. Recorrer curso por curso hacia
        // findById volvia el costo del guard proporcional a las inscripciones del estudiante.
        verify(courseDomain, never()).findById(any());
        verify(courseDomain, times(1)).isHomeroomTeacherOfAny(teacher, courses);
        verify(classGroupDomain, times(1)).teachesInAnyCourse(teacher, courses);
    }

    @Test
    void canReadStudent_director_bypassesWithoutLookup() {
        assertThat(authz.canReadStudent(
            token(UUID.randomUUID(), "Director"), UUID.randomUUID())).isTrue();
    }

    // ---- canWriteStudent ----

    @Test
    void canWriteStudent_homeroomTeacherOfOneOfTheStudentCourses_true() {
        UUID teacher = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        List<UUID> courses = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(courseEnrollmentDomain.courseIdsOfStudent(studentId)).thenReturn(courses);
        when(courseDomain.isHomeroomTeacherOfAny(teacher, courses)).thenReturn(true);

        assertThat(authz.canWriteStudent(token(teacher, "Teacher"), studentId)).isTrue();
    }

    @Test
    void canWriteStudent_technicalTeacherRunningAClassGroupThere_false() {
        UUID technicalTeacher = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        List<UUID> courses = List.of(UUID.randomUUID());
        when(courseEnrollmentDomain.courseIdsOfStudent(studentId)).thenReturn(courses);
        when(courseDomain.isHomeroomTeacherOfAny(technicalTeacher, courses)).thenReturn(false);

        // Dar de baja voltea toda inscripcion efectiva y saca al estudiante de las listas de
        // los demas docentes: dictar una materia ahi no alcanza, es acto del maestro de aula.
        assertThat(authz.canWriteStudent(token(technicalTeacher, "Teacher"), studentId)).isFalse();
        verify(classGroupDomain, never()).teachesInAnyCourse(any(), any());
    }

    @Test
    void canWriteStudent_secretary_false() {
        assertThat(authz.canWriteStudent(
            token(UUID.randomUUID(), "Secretary"), UUID.randomUUID())).isFalse();
    }

    @Test
    void canWriteStudent_director_bypassesWithoutLookup() {
        assertThat(authz.canWriteStudent(
            token(UUID.randomUUID(), "Director"), UUID.randomUUID())).isTrue();
    }

    @Test
    void canWriteStudent_asksTheStoreOnce_noMatterHowManyCoursesTheStudentIsIn() {
        UUID teacher = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        List<UUID> courses = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(courseEnrollmentDomain.courseIdsOfStudent(studentId)).thenReturn(courses);
        when(courseDomain.isHomeroomTeacherOfAny(teacher, courses)).thenReturn(true);

        assertThat(authz.canWriteStudent(token(teacher, "Teacher"), studentId)).isTrue();

        verify(courseDomain, never()).findById(any());
        verify(courseDomain, times(1)).isHomeroomTeacherOfAny(teacher, courses);
    }

    @Test
    void canReadScoreEvent_secretary_true() {
        // SecurityConfig admits Secretary on GET /api/scores/**; the method guard has to agree or
        // the role rule is dead and the documented scope is a lie.
        assertThat(authz.canReadScoreEvent(
            token(UUID.randomUUID(), "Secretary"), UUID.randomUUID())).isTrue();
    }

    @Test
    void canWriteDailyBatch_director_bypassesWithoutLookup() {
        UUID director = UUID.randomUUID();
        assertThat(authz.canWriteDailyBatch(token(director, "Director"),
            List.of(UUID.randomUUID(), UUID.randomUUID()))).isTrue();
    }

    // ---- canReadTeacherRoster ----

    @Test
    void canReadTeacherRoster_self_true() {
        UUID teacherA = UUID.randomUUID();
        assertThat(authz.canReadTeacherRoster(token(teacherA, "Teacher"), teacherA)).isTrue();
    }

    @Test
    void canReadTeacherRoster_foreign_false() {
        UUID teacherA = UUID.randomUUID();
        UUID teacherB = UUID.randomUUID();
        assertThat(authz.canReadTeacherRoster(token(teacherA, "Teacher"), teacherB)).isFalse();
    }

    @Test
    void canReadTeacherRoster_director_true() {
        UUID director = UUID.randomUUID();
        UUID anyTeacher = UUID.randomUUID();
        assertThat(authz.canReadTeacherRoster(token(director, "Director"), anyTeacher)).isTrue();
    }

    // ---- canReadCourse ----

    @Test
    void canReadCourse_director_bypassesWithoutLookup() {
        UUID director = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        assertThat(authz.canReadCourse(token(director, "Director"), courseId)).isTrue();
    }

    @Test
    void canReadCourse_homeroomTeacher_true() {
        UUID homeroomTeacher = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(courseDomain.findById(courseId)).thenReturn(
            Optional.of(new Course(courseId, 1, "Primero", 1, "A", 1, 2026, homeroomTeacher, "Ana", true)));

        assertThat(authz.canReadCourse(token(homeroomTeacher, "Teacher"), courseId)).isTrue();
    }

    @Test
    void canReadCourse_nonOwnerTeacher_false() {
        UUID homeroomTeacher = UUID.randomUUID();
        UUID otherTeacher = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(courseDomain.findById(courseId)).thenReturn(
            Optional.of(new Course(courseId, 1, "Primero", 1, "A", 1, 2026, homeroomTeacher, "Ana", true)));

        assertThat(authz.canReadCourse(token(otherTeacher, "Teacher"), courseId)).isFalse();
    }

    @Test
    void canReadCourse_unknownCourseId_deniesNotThrows() {
        UUID teacherA = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(courseDomain.findById(courseId)).thenReturn(Optional.empty());

        assertThat(authz.canReadCourse(token(teacherA, "Teacher"), courseId)).isFalse();
    }

    // ---- Secretary: school-wide read actor, zero write ----

    @Test
    void canReadCourse_secretary_true_forAnyCourseItDoesNotOwn() {
        // No stub on courseDomain: the secretariat is school-wide, so the check must short-circuit
        // before any ownership lookup.
        assertThat(authz.canReadCourse(
            token(UUID.randomUUID(), "Secretary"), UUID.randomUUID())).isTrue();
    }

    @Test
    void canReadEnrollmentScope_secretary_true_forAnyEnrollment() {
        assertThat(authz.canReadEnrollmentScope(
            token(UUID.randomUUID(), "Secretary"), UUID.randomUUID())).isTrue();
    }

    @Test
    void effectiveDirectoryCourseId_secretary_keepsRequestedCourse() {
        UUID requested = UUID.randomUUID();

        // A teacher would be pinned to their own homeroom here; the secretariat is not.
        assertThat(authz.effectiveDirectoryCourseId(
            token(UUID.randomUUID(), "Secretary"), requested)).isEqualTo(requested);
    }

    @Test
    void canWriteDailyAttendance_secretary_false() {
        UUID courseId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        when(courseEnrollmentDomain.courseOfEnrollment(enrollmentId)).thenReturn(courseId);
        when(courseDomain.findById(courseId)).thenReturn(
            Optional.of(new Course(courseId, 1, "Primero", 1, "A", 1, 2026, UUID.randomUUID(), "Ana", true)));

        // Read access must never leak into the write predicates: they share ownsEnrollmentCourse,
        // which is exactly why the Secretary bypass lives at the read entry points instead.
        assertThat(authz.canWriteDailyAttendance(
            token(UUID.randomUUID(), "Secretary"), enrollmentId)).isFalse();
    }

    @Test
    void canWriteClassGroup_secretary_false() {
        UUID classGroupId = UUID.randomUUID();
        when(classGroupDomain.teacherIdOfClassGroup(classGroupId)).thenReturn(UUID.randomUUID());

        assertThat(authz.canWriteClassGroup(
            token(UUID.randomUUID(), "Secretary"), classGroupId)).isFalse();
    }

    @Test
    void canWriteScoreEvent_secretary_false() {
        UUID eventId = UUID.randomUUID();
        UUID classGroupId = UUID.randomUUID();
        when(assessmentEventDomain.findById(eventId)).thenReturn(
            Optional.of(new AssessmentEvent(eventId, UUID.randomUUID(), classGroupId, 1, "Knowing", "t")));
        when(classGroupDomain.teacherIdOfClassGroup(classGroupId)).thenReturn(UUID.randomUUID());

        assertThat(authz.canWriteScoreEvent(
            token(UUID.randomUUID(), "Secretary"), eventId)).isFalse();
    }
    // ---- canReadPdc / canWritePdc ----

    private Pdc pdc(UUID id, UUID classGroupId) {
        return Pdc.builder().id(id).classGroupId(classGroupId).build();
    }

    @Test
    void canWritePdc_teacherOfTheClassGroup_true() {
        UUID teacher = UUID.randomUUID();
        UUID pdcId = UUID.randomUUID();
        UUID classGroupId = UUID.randomUUID();
        when(pdcDomain.findById(pdcId)).thenReturn(Optional.of(pdc(pdcId, classGroupId)));
        when(classGroupDomain.teacherIdOfClassGroup(classGroupId)).thenReturn(teacher);

        assertThat(authz.canWritePdc(token(teacher, "Teacher"), pdcId)).isTrue();
    }

    @Test
    void canWritePdc_anotherTeachersPlan_false() {
        UUID owner = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();
        UUID pdcId = UUID.randomUUID();
        UUID classGroupId = UUID.randomUUID();
        when(pdcDomain.findById(pdcId)).thenReturn(Optional.of(pdc(pdcId, classGroupId)));
        when(classGroupDomain.teacherIdOfClassGroup(classGroupId)).thenReturn(owner);

        assertThat(authz.canWritePdc(token(intruder, "Teacher"), pdcId)).isFalse();
    }

    @Test
    void canWritePdc_unknownPlan_deniesNotThrows() {
        UUID teacher = UUID.randomUUID();
        UUID pdcId = UUID.randomUUID();
        when(pdcDomain.findById(pdcId)).thenReturn(Optional.empty());

        assertThat(authz.canWritePdc(token(teacher, "Teacher"), pdcId)).isFalse();
    }

    @Test
    void canWritePdc_secretary_false() {
        // Read-only staff: reaches the record, writes nothing anywhere.
        assertThat(authz.canWritePdc(token(UUID.randomUUID(), "Secretary"), UUID.randomUUID())).isFalse();
    }

    @Test
    void canWritePdc_director_bypassesWithoutLookup() {
        assertThat(authz.canWritePdc(token(UUID.randomUUID(), "Director"), UUID.randomUUID())).isTrue();
    }

    @Test
    void canReadPdc_secretary_true() {
        assertThat(authz.canReadPdc(token(UUID.randomUUID(), "Secretary"), UUID.randomUUID())).isTrue();
    }

    @Test
    void canReadPdc_anotherTeachersPlan_false() {
        UUID owner = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();
        UUID pdcId = UUID.randomUUID();
        UUID classGroupId = UUID.randomUUID();
        when(pdcDomain.findById(pdcId)).thenReturn(Optional.of(pdc(pdcId, classGroupId)));
        when(classGroupDomain.teacherIdOfClassGroup(classGroupId)).thenReturn(owner);

        assertThat(authz.canReadPdc(token(intruder, "Teacher"), pdcId)).isFalse();
    }

    // ---- pdcListScopeTeacherId ----

    @Test
    void pdcListScope_teacher_isNarrowedToTheirOwnPlans() {
        UUID teacher = UUID.randomUUID();

        assertThat(authz.pdcListScopeTeacherId(token(teacher, "Teacher"))).isEqualTo(teacher);
    }

    @Test
    void pdcListScope_directorAndSecretary_seeEveryPlan() {
        assertThat(authz.pdcListScopeTeacherId(token(UUID.randomUUID(), "Director"))).isNull();
        assertThat(authz.pdcListScopeTeacherId(token(UUID.randomUUID(), "Secretary"))).isNull();
    }

    @Test
    void pdcListScope_tokenWithoutUsableSubject_matchesNothing() {
        // A scope of null would widen the listing to the whole school, so an unusable token has to
        // resolve to an id no plan can carry instead.
        assertThat(authz.pdcListScopeTeacherId(null)).isEqualTo(new UUID(0L, 0L));
    }

    // ---- canReadAdaptation / canWriteAdaptation ----

    @Test
    void canWriteAdaptation_teacherOfThePlansClassGroup_true() {
        UUID teacher = UUID.randomUUID();
        UUID adaptationId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID classGroupId = UUID.randomUUID();
        when(adaptationDomain.findById(adaptationId)).thenReturn(Optional.of(
            new Adaptation(adaptationId, planId, UUID.randomUUID(), "Ana Perez",
                null, null, null, null, null, null, null)));
        when(pdcDomain.findById(planId)).thenReturn(Optional.of(pdc(planId, classGroupId)));
        when(classGroupDomain.teacherIdOfClassGroup(classGroupId)).thenReturn(teacher);

        assertThat(authz.canWriteAdaptation(token(teacher, "Teacher"), adaptationId)).isTrue();
    }

    @Test
    void canWriteAdaptation_anotherTeachersStudent_false() {
        UUID owner = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();
        UUID adaptationId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID classGroupId = UUID.randomUUID();
        when(adaptationDomain.findById(adaptationId)).thenReturn(Optional.of(
            new Adaptation(adaptationId, planId, UUID.randomUUID(), "Ana Perez",
                null, null, null, null, null, null, null)));
        when(pdcDomain.findById(planId)).thenReturn(Optional.of(pdc(planId, classGroupId)));
        when(classGroupDomain.teacherIdOfClassGroup(classGroupId)).thenReturn(owner);

        // The adaptation carries the student's full name, so reading it is disclosure.
        assertThat(authz.canWriteAdaptation(token(intruder, "Teacher"), adaptationId)).isFalse();
        assertThat(authz.canReadAdaptation(token(intruder, "Teacher"), adaptationId)).isFalse();
    }

    @Test
    void canWriteAdaptation_unknownAdaptation_deniesNotThrows() {
        UUID teacher = UUID.randomUUID();
        UUID adaptationId = UUID.randomUUID();
        when(adaptationDomain.findById(adaptationId)).thenReturn(Optional.empty());

        assertThat(authz.canWriteAdaptation(token(teacher, "Teacher"), adaptationId)).isFalse();
    }

    @Test
    void canReadAdaptation_secretary_true() {
        assertThat(authz.canReadAdaptation(token(UUID.randomUUID(), "Secretary"), UUID.randomUUID())).isTrue();
    }
    // ---- canActOnNotification ----

    @Test
    void canActOnNotification_theReceiver_true() {
        UUID receiver = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(notificationDomain.findById(id)).thenReturn(Optional.of(
            new Notification(id, UUID.randomUUID(), null, receiver, null, "hola", false, null)));

        assertThat(authz.canActOnNotification(token(receiver, "Teacher"), id)).isTrue();
    }

    @Test
    void canActOnNotification_someoneElsesInbox_false() {
        UUID receiver = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(notificationDomain.findById(id)).thenReturn(Optional.of(
            new Notification(id, UUID.randomUUID(), null, receiver, null, "hola", false, null)));

        assertThat(authz.canActOnNotification(token(intruder, "Teacher"), id)).isFalse();
    }

    @Test
    void canActOnNotification_notEvenTheDirector_false() {
        UUID receiver = UUID.randomUUID();
        UUID director = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(notificationDomain.findById(id)).thenReturn(Optional.of(
            new Notification(id, UUID.randomUUID(), null, receiver, null, "hola", false, null)));

        // Acting on someone else's inbox is not an act of authority.
        assertThat(authz.canActOnNotification(token(director, "Director"), id)).isFalse();
    }

    @Test
    void canActOnNotification_unknownNotification_deniesNotThrows() {
        UUID id = UUID.randomUUID();
        when(notificationDomain.findById(id)).thenReturn(Optional.empty());

        assertThat(authz.canActOnNotification(token(UUID.randomUUID(), "Teacher"), id)).isFalse();
    }
}
