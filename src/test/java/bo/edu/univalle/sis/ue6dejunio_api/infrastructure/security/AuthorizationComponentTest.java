package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.security;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentEvent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentEventDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentScoreDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationComponentTest {

    @Mock private IClassGroupDomain classGroupDomain;
    @Mock private IAssessmentEventDomain assessmentEventDomain;
    @Mock private IAssessmentScoreDomain assessmentScoreDomain;
    @Mock private ICourseEnrollmentDomain courseEnrollmentDomain;
    @Mock private ICourseDomain courseDomain;

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
            Optional.of(new AssessmentEvent(eventId, UUID.randomUUID(), classGroupId, 1, "Knowing", "t", null, BigDecimal.TEN)));
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
            Optional.of(new AssessmentEvent(eventId, UUID.randomUUID(), classGroupId, 1, "Knowing", "t", null, BigDecimal.TEN)));
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
            Optional.of(new AssessmentScore(scoreId, UUID.randomUUID(), eventId, BigDecimal.TEN, null, null)));
        when(assessmentEventDomain.findById(eventId)).thenReturn(
            Optional.of(new AssessmentEvent(eventId, UUID.randomUUID(), classGroupId, 1, "Knowing", "t", null, BigDecimal.TEN)));
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
        when(courseEnrollmentDomain.courseOfEnrollment(any())).thenReturn(courseId);
        when(courseDomain.findById(courseId)).thenReturn(
            Optional.of(new Course(courseId, 1, "Primero", 1, "A", 1, 2026, homeroomTeacher, "Ana", true)));

        assertThat(authz.canWriteDailyBatch(token(homeroomTeacher, "Teacher"), List.of(ce1, ce2))).isTrue();
    }

    @Test
    void canWriteDailyBatch_mixedOwnership_deniesWhole() {
        UUID homeroomTeacherA = UUID.randomUUID();
        UUID ceOwned = UUID.randomUUID();
        UUID ceForeign = UUID.randomUUID();
        UUID courseA = UUID.randomUUID();
        UUID courseB = UUID.randomUUID();
        when(courseEnrollmentDomain.courseOfEnrollment(ceOwned)).thenReturn(courseA);
        when(courseEnrollmentDomain.courseOfEnrollment(ceForeign)).thenReturn(courseB);
        when(courseDomain.findById(courseA)).thenReturn(
            Optional.of(new Course(courseA, 1, "Primero", 1, "A", 1, 2026, homeroomTeacherA, "Ana", true)));
        when(courseDomain.findById(courseB)).thenReturn(
            Optional.of(new Course(courseB, 1, "Primero", 1, "B", 1, 2026, UUID.randomUUID(), "Otro", true)));

        assertThat(authz.canWriteDailyBatch(token(homeroomTeacherA, "Teacher"), List.of(ceOwned, ceForeign))).isFalse();
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
}
