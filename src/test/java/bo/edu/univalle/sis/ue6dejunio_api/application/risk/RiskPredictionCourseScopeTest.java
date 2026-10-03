package bo.edu.univalle.sis.ue6dejunio_api.application.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.risk.RiskPredictionService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskLevel;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskPrediction;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.StudentRisk;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskFeatureDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskModelClient;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskPredictionDomain;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Which rows of a course's risk list a reader gets: all of them for the staff and the homeroom
 * teacher, only their own subjects for any other teacher.
 */
@ExtendWith(MockitoExtension.class)
class RiskPredictionCourseScopeTest {

    @Mock private IRiskFeatureDomain featureDomain;
    @Mock private IRiskModelClient modelClient;
    @Mock private IRiskPredictionDomain predictionDomain;
    @Mock private INotificationService notifications;
    @Mock private IClassGroupDomain classGroupDomain;
    @Mock private ICourseService courseService;
    @Mock private ICourseEnrollmentDomain enrollmentDomain;
    @Mock private ClosedYearGuard closedYearGuard;

    private RiskPredictionService service;

    private final UUID courseId = UUID.randomUUID();
    private final UUID homeroom = UUID.randomUUID();
    private final UUID technical = UUID.randomUUID();
    private final UUID mathGroup = UUID.randomUUID();
    private final UUID languageGroup = UUID.randomUUID();
    private final UUID workshopGroup = UUID.randomUUID();

    private StudentRisk math;
    private StudentRisk language;
    private StudentRisk workshop;

    @BeforeEach
    void setUp() {
        service =
                new RiskPredictionService(
                        featureDomain,
                        modelClient,
                        predictionDomain,
                        notifications,
                        classGroupDomain,
                        courseService,
                        enrollmentDomain,
                        Clock.systemUTC(),
                        closedYearGuard);
        math = row(mathGroup, "Matematicas");
        language = row(languageGroup, "Lenguaje");
        workshop = row(workshopGroup, "Taller");
        when(predictionDomain.byCourseAndTrimester(courseId, 1))
                .thenReturn(List.of(math, language, workshop));
    }

    private static StudentRisk row(UUID classGroupId, String subject) {
        RiskPrediction prediction =
                new RiskPrediction(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        classGroupId,
                        1,
                        RiskLevel.EN_RIESGO,
                        new BigDecimal("0.5"),
                        new BigDecimal("0.1"),
                        false,
                        "{}",
                        LocalDateTime.of(2026, 4, 10, 8, 0));
        return new StudentRisk(prediction, "Ana", "Alvarez", subject);
    }

    private Course courseWithHomeroom(UUID homeroomTeacherId) {
        return new Course(
                courseId, 1, "Quinto", 1, "A", 1, 2026, homeroomTeacherId, "X", true, true);
    }

    @Test
    void noScope_staffReadsEveryRowWithoutAnyOwnershipLookup() {
        assertThat(service.byCourse(courseId, 1, null)).containsExactly(math, language, workshop);

        verifyNoInteractions(classGroupDomain, courseService);
    }

    @Test
    void homeroomTeacher_readsEverySubject() {
        when(courseService.getById(courseId)).thenReturn(courseWithHomeroom(homeroom));

        assertThat(service.byCourse(courseId, 1, homeroom))
                .containsExactly(math, language, workshop);

        verify(classGroupDomain, never()).classGroupIdsTaughtInCourse(homeroom, courseId);
    }

    @Test
    void otherTeacher_readsOnlyTheSubjectsTheyTeach_inOneBatchedLookup() {
        when(courseService.getById(courseId)).thenReturn(courseWithHomeroom(homeroom));
        when(classGroupDomain.classGroupIdsTaughtInCourse(technical, courseId))
                .thenReturn(Set.of(languageGroup, workshopGroup));

        assertThat(service.byCourse(courseId, 1, technical)).containsExactly(language, workshop);

        verify(classGroupDomain).classGroupIdsTaughtInCourse(technical, courseId);
    }

    @Test
    void teacherWithNoGroupInTheCourse_readsNothing() {
        when(courseService.getById(courseId)).thenReturn(courseWithHomeroom(homeroom));
        when(classGroupDomain.classGroupIdsTaughtInCourse(technical, courseId))
                .thenReturn(Set.of());

        assertThat(service.byCourse(courseId, 1, technical)).isEmpty();
    }
}
