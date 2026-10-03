package bo.edu.univalle.sis.ue6dejunio_api.application.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.risk.RiskPredictionService;
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
 * Which rows of one student's risk list a reader gets: all of them for the staff, only the subjects
 * a teacher runs, or the whole of a course they are homeroom of, for any other teacher.
 */
@ExtendWith(MockitoExtension.class)
class RiskPredictionStudentScopeTest {

    @Mock private IRiskFeatureDomain featureDomain;
    @Mock private IRiskModelClient modelClient;
    @Mock private IRiskPredictionDomain predictionDomain;
    @Mock private INotificationService notifications;
    @Mock private IClassGroupDomain classGroupDomain;
    @Mock private ICourseService courseService;
    @Mock private ICourseEnrollmentDomain enrollmentDomain;
    @Mock private ClosedYearGuard closedYearGuard;

    private RiskPredictionService service;

    private final UUID studentId = UUID.randomUUID();
    private final UUID courseA = UUID.randomUUID();
    private final UUID courseB = UUID.randomUUID();
    private final UUID teacher = UUID.randomUUID();
    private final UUID mathGroup = UUID.randomUUID();
    private final UUID languageGroup = UUID.randomUUID();

    private StudentRisk math;
    private StudentRisk language;

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
        when(predictionDomain.byStudent(studentId)).thenReturn(List.of(math, language));
    }

    private StudentRisk row(UUID classGroupId, String subject) {
        RiskPrediction prediction =
                new RiskPrediction(
                        UUID.randomUUID(),
                        studentId,
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

    @Test
    void noScope_staffReadsEveryRowWithoutAnyOwnershipLookup() {
        assertThat(service.byStudent(studentId, null)).containsExactly(math, language);

        verifyNoInteractions(classGroupDomain, enrollmentDomain, courseService);
    }

    @Test
    void teacher_readsOnlyWhatTheyMayReadInTheStudentsCourses_inOneBatchedLookup() {
        when(enrollmentDomain.courseIdsOfStudent(studentId)).thenReturn(List.of(courseA, courseB));
        when(classGroupDomain.classGroupIdsReadableByTeacherInCourses(
                        teacher, List.of(courseA, courseB)))
                .thenReturn(Set.of(languageGroup));

        assertThat(service.byStudent(studentId, teacher)).containsExactly(language);
    }

    @Test
    void teacherWithNothingReadable_readsNothing() {
        when(enrollmentDomain.courseIdsOfStudent(studentId)).thenReturn(List.of(courseA));
        when(classGroupDomain.classGroupIdsReadableByTeacherInCourses(teacher, List.of(courseA)))
                .thenReturn(Set.of());

        assertThat(service.byStudent(studentId, teacher)).isEmpty();
    }
}
