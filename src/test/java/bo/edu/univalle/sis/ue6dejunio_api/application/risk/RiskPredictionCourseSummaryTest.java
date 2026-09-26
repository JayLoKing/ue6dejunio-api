package bo.edu.univalle.sis.ue6dejunio_api.application.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.risk.RiskPredictionService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.CourseRiskSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.CourseStudentRisk;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Risk counted by classroom, for the Director's table.
 *
 * <p>The one thing these tests exist to pin is the grain. The model answers one row per subject, so
 * a child failing three areas leaves three RiesgoCritico rows; counted as rows, a classroom of
 * twenty would report more students at risk than it has students. Each child is counted once, in
 * their worst band.
 *
 * <p>Pure unit test: no DB, no model, no Testcontainers.
 */
@ExtendWith(MockitoExtension.class)
class RiskPredictionCourseSummaryTest {

    @Mock private IRiskFeatureDomain featureDomain;
    @Mock private IRiskModelClient modelClient;
    @Mock private IRiskPredictionDomain predictionDomain;
    @Mock private INotificationService notifications;
    @Mock private IClassGroupDomain classGroupDomain;
    @Mock private ICourseService courseService;
    @Mock private ICourseEnrollmentDomain enrollmentDomain;

    private static final Integer ACADEMIC_YEAR_ID = 7;
    private static final int TRIMESTER = 1;

    private RiskPredictionService service;
    private UUID quinto;
    private UUID sexto;

    private final List<CourseStudentRisk> gestionRisks = new ArrayList<>();
    private final Map<UUID, Long> enrolments = new HashMap<>();

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
                        Clock.systemDefaultZone());
        quinto = UUID.randomUUID();
        sexto = UUID.randomUUID();
    }

    @Test
    void courseRiskSummaries_refusesWithoutAGestion() {
        assertThatThrownBy(() -> service.courseRiskSummaries(null, TRIMESTER))
                .isInstanceOf(ValidationException.class);
    }

    /**
     * The grain, stated as a test. Ana fails three subjects; she is one student in critical
     * trouble, not three.
     */
    @Test
    void courseRiskSummaries_countsAStudentOnceInTheirWorstBand() {
        coursesOfTheYear(course(quinto, "Quinto", "B"));
        UUID ana = UUID.randomUUID();
        rosterOf(quinto, studentOf(ana));
        risksOf(
                quinto,
                riskOf(ana, RiskLevel.RIESGO_CRITICO),
                riskOf(ana, RiskLevel.RIESGO_CRITICO),
                riskOf(ana, RiskLevel.RIESGO_CRITICO));

        assertThat(summaries())
                .singleElement()
                .satisfies(
                        s -> {
                            assertThat(s.critical()).isEqualTo(1);
                            assertThat(s.atRisk()).isZero();
                        });
    }

    /**
     * The worst band wins: a child failing one area is at critical risk, whatever else they pass.
     */
    @Test
    void courseRiskSummaries_takesTheWorstOfAStudentsSubjects() {
        coursesOfTheYear(course(quinto, "Quinto", "B"));
        UUID ana = UUID.randomUUID();
        rosterOf(quinto, studentOf(ana));
        risksOf(
                quinto,
                riskOf(ana, RiskLevel.SOBRESALIENTE),
                riskOf(ana, RiskLevel.SIN_RIESGO),
                riskOf(ana, RiskLevel.RIESGO_CRITICO));

        assertThat(summaries().get(0).critical()).isEqualTo(1);
        assertThat(summaries().get(0).outstanding()).isZero();
    }

    @Test
    void courseRiskSummaries_sortsEachStudentIntoTheirBand() {
        coursesOfTheYear(course(quinto, "Quinto", "B"));
        UUID ana = UUID.randomUUID();
        UUID beto = UUID.randomUUID();
        UUID carla = UUID.randomUUID();
        UUID dario = UUID.randomUUID();
        rosterOf(quinto, studentOf(ana), studentOf(beto), studentOf(carla), studentOf(dario));
        risksOf(
                quinto,
                riskOf(ana, RiskLevel.RIESGO_CRITICO),
                riskOf(beto, RiskLevel.EN_RIESGO),
                riskOf(carla, RiskLevel.SIN_RIESGO),
                riskOf(dario, RiskLevel.SOBRESALIENTE));

        assertThat(summaries())
                .singleElement()
                .satisfies(
                        s -> {
                            assertThat(s.critical()).isEqualTo(1);
                            assertThat(s.atRisk()).isEqualTo(1);
                            assertThat(s.safe()).isEqualTo(1);
                            assertThat(s.outstanding()).isEqualTo(1);
                            assertThat(s.unpredicted()).isZero();
                        });
    }

    /**
     * A classroom the sweep has barely reached is not a safe classroom.
     *
     * <p>Four bands adding up to two, in a room of five, would read as a room of two. The
     * unpredicted are counted so the reader can tell "nobody is in trouble" from "nobody has been
     * looked at".
     */
    @Test
    void courseRiskSummaries_countsTheStudentsTheModelHasNotReached() {
        coursesOfTheYear(course(quinto, "Quinto", "B"));
        UUID ana = UUID.randomUUID();
        rosterOf(
                quinto, studentOf(ana), studentOf(UUID.randomUUID()), studentOf(UUID.randomUUID()));
        risksOf(quinto, riskOf(ana, RiskLevel.EN_RIESGO));

        assertThat(summaries())
                .singleElement()
                .satisfies(
                        s -> {
                            assertThat(s.atRisk()).isEqualTo(1);
                            assertThat(s.unpredicted()).isEqualTo(2);
                        });
    }

    /** Every classroom of the gestión has a row, including the one nobody predicted. */
    @Test
    void courseRiskSummaries_listsEveryClassroom() {
        coursesOfTheYear(course(quinto, "Quinto", "B"), course(sexto, "Sexto", "A"));
        rosterOf(quinto, studentOf(UUID.randomUUID()));
        rosterOf(sexto, studentOf(UUID.randomUUID()));
        risksOf(quinto);

        assertThat(summaries())
                .extracting(CourseRiskSummary::parallelName)
                .containsExactly("B", "A");
    }

    /**
     * The whole gestión in one read of the predictions — the same single query the school-wide risk
     * list already uses, and not one per classroom.
     */
    @Test
    void courseRiskSummaries_readsEveryPredictionInOneQuery() {
        coursesOfTheYear(course(quinto, "Quinto", "B"), course(sexto, "Sexto", "A"));
        rosterOf(quinto, studentOf(UUID.randomUUID()));
        rosterOf(sexto, studentOf(UUID.randomUUID()));
        risksOf(quinto);

        summaries();

        verify(predictionDomain, times(1)).byAcademicYearAndTrimester(ACADEMIC_YEAR_ID, TRIMESTER);
        verify(predictionDomain, never()).byCourseAndTrimester(any(), anyInt());
    }

    // ---------------------------------------------------------------- fixtures

    private List<CourseRiskSummary> summaries() {
        return service.courseRiskSummaries(ACADEMIC_YEAR_ID, TRIMESTER);
    }

    private void coursesOfTheYear(Course... courses) {
        when(courseService.allOfYear(ACADEMIC_YEAR_ID)).thenReturn(List.of(courses));
    }

    /**
     * How many students each classroom holds, accumulated across calls and restubbed as the single
     * grouped count the service asks for.
     */
    private void rosterOf(UUID courseId, CourseStudent... students) {
        enrolments.put(courseId, (long) students.length);
        when(enrollmentDomain.enrolmentCountsByCourse(ACADEMIC_YEAR_ID))
                .thenReturn(Map.copyOf(enrolments));
    }

    private void risksOf(UUID courseId, StudentRisk... risks) {
        for (StudentRisk risk : risks) {
            gestionRisks.add(new CourseStudentRisk(courseId, risk));
        }
        when(predictionDomain.byAcademicYearAndTrimester(ACADEMIC_YEAR_ID, TRIMESTER))
                .thenReturn(List.copyOf(gestionRisks));
    }

    private static CourseStudent studentOf(UUID studentId) {
        return new CourseStudent(
                UUID.randomUUID(), studentId, "RUDE", "ID", "Ana", "Perez", "Effective", "F");
    }

    private static StudentRisk riskOf(UUID studentId, RiskLevel level) {
        RiskPrediction prediction =
                new RiskPrediction(
                        UUID.randomUUID(),
                        studentId,
                        UUID.randomUUID(),
                        TRIMESTER,
                        level,
                        new BigDecimal("0.50"),
                        new BigDecimal("0.01"),
                        false,
                        "{}",
                        LocalDateTime.of(2026, 4, 10, 8, 0));
        return new StudentRisk(prediction, "Ana", "Perez", "Lenguaje");
    }

    private static Course course(UUID id, String gradeName, String parallelName) {
        return new Course(
                id,
                5,
                gradeName,
                2,
                parallelName,
                ACADEMIC_YEAR_ID,
                2026,
                UUID.randomUUID(),
                "Ana Perez",
                true);
    }
}
