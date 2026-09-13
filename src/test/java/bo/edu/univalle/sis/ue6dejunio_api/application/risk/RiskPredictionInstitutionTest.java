package bo.edu.univalle.sis.ue6dejunio_api.application.risk;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.risk.RiskPredictionService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.InstitutionRiskEntry;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskLevel;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskPrediction;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.StudentRisk;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskFeatureDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskModelClient;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskPredictionDomain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * The risk list of the whole school: the students closest to failing, worst first.
 *
 * <p>Its grain is one row per student and not one per subject, which is the single thing that most
 * separates it from the panel a teacher opens. A course panel is read by somebody who teaches those
 * nine subjects and wants all nine; a school-wide top ten read at subject grain can be one child
 * listed ten times, and the nine other children it pushed off the list are the ones the Director
 * opened it to find.
 *
 * <p>Pure unit test: no DB, no model, no Testcontainers.
 */
@ExtendWith(MockitoExtension.class)
class RiskPredictionInstitutionTest {

    @Mock private IRiskFeatureDomain featureDomain;
    @Mock private IRiskModelClient modelClient;
    @Mock private IRiskPredictionDomain predictionDomain;
    @Mock private INotificationService notifications;
    @Mock private IClassGroupDomain classGroupDomain;
    @Mock private ICourseService courseService;

    /** The row id of the academic year, not the calendar year — {@code id_academic_year} is a SERIAL. */
    private static final Integer ACADEMIC_YEAR_ID = 7;
    private static final int TRIMESTER = 1;

    private RiskPredictionService service;

    private UUID quintoId;
    private UUID sextoId;

    @BeforeEach
    void buildService() {
        service = new RiskPredictionService(featureDomain, modelClient, predictionDomain,
            notifications, classGroupDomain, courseService);
        quintoId = UUID.randomUUID();
        sextoId = UUID.randomUUID();
    }

    /**
     * Without a gestión the course listing answers every year at once, and the list would rank a
     * student of 2024 beside one of 2026. It is also what makes the paged course read sound, exactly
     * as it is for the cuadro de honor.
     */
    @Test
    void institutionRisk_refusesToBuildAListWithoutAGestion() {
        assertThatThrownBy(() -> service.institutionRisk(null, TRIMESTER, 10))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void institutionRisk_putsTheWorstFirstAndCutsAtThePlacesAsked() {
        coursesOfTheYear(course(quintoId, "Quinto", "B"));
        risksOf(quintoId,
            risk("Ana", "Alvarez", "Matematicas", "0.91", RiskLevel.RIESGO_CRITICO),
            risk("Bruno", "Bermudez", "Lenguaje", "0.74", RiskLevel.RIESGO_CRITICO),
            risk("Carla", "Cruz", "Fisica", "0.55", RiskLevel.EN_RIESGO));

        List<InstitutionRiskEntry> worst = service.institutionRisk(ACADEMIC_YEAR_ID, TRIMESTER, 2);

        assertThat(worst).extracting(InstitutionRiskEntry::fullName)
            .containsExactly("Alvarez Ana", "Bermudez Bruno");
        assertThat(worst).extracting(InstitutionRiskEntry::position).containsExactly(1, 2);
        assertThat(worst.get(0).pFail()).isEqualByComparingTo("0.91");
    }

    /**
     * The grain. A student sits nine subjects and can be predicted in all nine, but they are one
     * child in trouble, not nine — and the subject that comes with them is the worst of theirs,
     * because that is the one whoever reads this list has to act on.
     */
    @Test
    void institutionRisk_countsAStudentOnceAndNamesTheirWorstSubject() {
        coursesOfTheYear(course(quintoId, "Quinto", "B"));
        UUID ana = UUID.randomUUID();
        risksOf(quintoId,
            riskOf(ana, "Ana", "Alvarez", "Lenguaje", "0.60", RiskLevel.EN_RIESGO),
            riskOf(ana, "Ana", "Alvarez", "Matematicas", "0.91", RiskLevel.RIESGO_CRITICO),
            riskOf(ana, "Ana", "Alvarez", "Fisica", "0.80", RiskLevel.RIESGO_CRITICO));

        List<InstitutionRiskEntry> worst = service.institutionRisk(ACADEMIC_YEAR_ID, TRIMESTER, 10);

        assertThat(worst).singleElement().satisfies(entry -> {
            assertThat(entry.studentId()).isEqualTo(ana);
            assertThat(entry.subjectName()).isEqualTo("Matematicas");
            assertThat(entry.pFail()).isEqualByComparingTo("0.91");
            assertThat(entry.riskLevel()).isEqualTo(RiskLevel.RIESGO_CRITICO);
        });
    }

    /**
     * The school's list is not the courses' lists concatenated: it is the worst of the building.
     * Taking the same number of places from every course before merging is exact and not an
     * approximation — a student who is eleventh in their own classroom already has ten worse ahead
     * of them, so they cannot be in the school's worst ten.
     */
    @Test
    void institutionRisk_takesTheWorstOfEveryCourseAndCutsOnceMore() {
        coursesOfTheYear(course(quintoId, "Quinto", "B"), course(sextoId, "Sexto", "A"));
        risksOf(quintoId,
            risk("Ana", "Alvarez", "Matematicas", "0.70", RiskLevel.RIESGO_CRITICO),
            risk("Bruno", "Bermudez", "Lenguaje", "0.40", RiskLevel.EN_RIESGO));
        risksOf(sextoId,
            risk("Carla", "Cruz", "Fisica", "0.95", RiskLevel.RIESGO_CRITICO),
            risk("Dario", "Duran", "Quimica", "0.30", RiskLevel.EN_RIESGO));

        List<InstitutionRiskEntry> worst = service.institutionRisk(ACADEMIC_YEAR_ID, TRIMESTER, 2);

        assertThat(worst).extracting(InstitutionRiskEntry::fullName)
            .containsExactly("Cruz Carla", "Alvarez Ana");
    }

    /**
     * The grain again, across classrooms this time.
     *
     * <p>{@code course_enrollments} is unique on student and course, not on student and gestión, so
     * a student moved between parallels mid-year keeps an enrolment in both — and the predictions
     * filed against their old classroom do not disappear. Collapsing inside each course would let
     * that one child hold two of the school's ten places.
     */
    @Test
    void institutionRisk_countsAStudentOnceEvenAcrossTwoClassroomsOfTheSameYear() {
        coursesOfTheYear(course(quintoId, "Quinto", "B"), course(sextoId, "Sexto", "A"));
        UUID ana = UUID.randomUUID();
        risksOf(quintoId, riskOf(ana, "Ana", "Alvarez", "Lenguaje", "0.50", RiskLevel.EN_RIESGO));
        risksOf(sextoId, riskOf(ana, "Ana", "Alvarez", "Matematicas", "0.90", RiskLevel.RIESGO_CRITICO));

        List<InstitutionRiskEntry> worst = service.institutionRisk(ACADEMIC_YEAR_ID, TRIMESTER, 10);

        assertThat(worst).singleElement().satisfies(entry -> {
            assertThat(entry.subjectName()).isEqualTo("Matematicas");
            assertThat(entry.courseId()).isEqualTo(sextoId);
            assertThat(entry.position()).isEqualTo(1);
        });
    }

    /** A list that does not say which classroom a student sits in names nobody in a school of two Anas. */
    @Test
    void institutionRisk_namesTheCourseEachStudentCameFrom() {
        coursesOfTheYear(course(quintoId, "Quinto", "B"));
        risksOf(quintoId, risk("Ana", "Alvarez", "Matematicas", "0.91", RiskLevel.RIESGO_CRITICO));

        List<InstitutionRiskEntry> worst = service.institutionRisk(ACADEMIC_YEAR_ID, TRIMESTER, 10);

        assertThat(worst).singleElement().satisfies(entry -> {
            assertThat(entry.courseId()).isEqualTo(quintoId);
            assertThat(entry.gradeName()).isEqualTo("Quinto");
            assertThat(entry.parallelName()).isEqualTo("B");
        });
    }

    /**
     * The prediction's own id travels with the row. Attending one is per prediction and not per
     * screen — the Director acting on Ana's Matematicas must not mark her Lenguaje attended too —
     * and a row without it cannot be acted on at all.
     */
    @Test
    void institutionRisk_carriesThePredictionItsRowStandsFor() {
        coursesOfTheYear(course(quintoId, "Quinto", "B"));
        StudentRisk ana = risk("Ana", "Alvarez", "Matematicas", "0.91", RiskLevel.RIESGO_CRITICO);
        risksOf(quintoId, ana);

        List<InstitutionRiskEntry> worst = service.institutionRisk(ACADEMIC_YEAR_ID, TRIMESTER, 10);

        assertThat(worst).singleElement().satisfies(entry -> {
            assertThat(entry.predictionId()).isEqualTo(ana.prediction().id());
            assertThat(entry.classGroupId()).isEqualTo(ana.prediction().classGroupId());
            assertThat(entry.attended()).isFalse();
        });
    }

    /**
     * The same tie, one level down. Two of a student's subjects can sit on the same probability, and
     * whichever the query happened to return first is not an answer — the row would name Lenguaje on
     * one reading and Matematicas on the next, off the same unchanged predictions. The subject's own
     * name breaks it, so what decides it is something the reader can see.
     */
    @Test
    void institutionRisk_breaksATieBetweenTwoOfAStudentsSubjectsByName() {
        coursesOfTheYear(course(quintoId, "Quinto", "B"));
        UUID ana = UUID.randomUUID();
        risksOf(quintoId,
            riskOf(ana, "Ana", "Alvarez", "Matematicas", "0.80", RiskLevel.RIESGO_CRITICO),
            riskOf(ana, "Ana", "Alvarez", "Lenguaje", "0.80", RiskLevel.RIESGO_CRITICO));

        List<InstitutionRiskEntry> worst = service.institutionRisk(ACADEMIC_YEAR_ID, TRIMESTER, 10);

        assertThat(worst).singleElement()
            .extracting(InstitutionRiskEntry::subjectName).isEqualTo("Lenguaje");
    }

    /**
     * Two students on the same probability still have to come out in the same order on two readings,
     * or the school reads two different lists off one trimester. The name the list itself shows
     * breaks the tie, so what decides the order is something the reader can see.
     */
    @Test
    void institutionRisk_breaksATieByNameSoTheListHoldsStill() {
        coursesOfTheYear(course(quintoId, "Quinto", "B"));
        risksOf(quintoId,
            risk("Bruno", "Bermudez", "Lenguaje", "0.80", RiskLevel.RIESGO_CRITICO),
            risk("Ana", "Alvarez", "Matematicas", "0.80", RiskLevel.RIESGO_CRITICO));

        List<InstitutionRiskEntry> worst = service.institutionRisk(ACADEMIC_YEAR_ID, TRIMESTER, 2);

        assertThat(worst).extracting(InstitutionRiskEntry::fullName)
            .containsExactly("Alvarez Ana", "Bermudez Bruno");
    }

    /**
     * A prediction with no probability was never scored, so it cannot be placed against ones that
     * were. Sorting it as a zero would say the model looked at the student and found them safe.
     */
    @Test
    void institutionRisk_leavesOutTheRowTheModelNeverScored() {
        coursesOfTheYear(course(quintoId, "Quinto", "B"));
        risksOf(quintoId,
            risk("Ana", "Alvarez", "Matematicas", null, RiskLevel.RIESGO_CRITICO),
            risk("Bruno", "Bermudez", "Lenguaje", "0.10", RiskLevel.SIN_RIESGO));

        List<InstitutionRiskEntry> worst = service.institutionRisk(ACADEMIC_YEAR_ID, TRIMESTER, 10);

        assertThat(worst).extracting(InstitutionRiskEntry::fullName).containsExactly("Bermudez Bruno");
    }

    @Test
    void institutionRisk_isEmptyWhenTheYearHasNoCourses() {
        when(courseService.allOfYear(ACADEMIC_YEAR_ID)).thenReturn(List.of());

        assertThat(service.institutionRisk(ACADEMIC_YEAR_ID, TRIMESTER, 10)).isEmpty();
    }

    // ---------------------------------------------------------------- fixtures

    private void coursesOfTheYear(Course... courses) {
        when(courseService.allOfYear(ACADEMIC_YEAR_ID)).thenReturn(List.of(courses));
    }

    private void risksOf(UUID courseId, StudentRisk... risks) {
        when(predictionDomain.byCourseAndTrimester(eq(courseId), eq(TRIMESTER)))
            .thenReturn(List.of(risks));
    }

    private static StudentRisk risk(String names, String lastNames, String subject, String pFail,
                                    RiskLevel level) {
        return riskOf(UUID.randomUUID(), names, lastNames, subject, pFail, level);
    }

    private static StudentRisk riskOf(UUID studentId, String names, String lastNames, String subject,
                                      String pFail, RiskLevel level) {
        RiskPrediction prediction = new RiskPrediction(UUID.randomUUID(), studentId,
            UUID.randomUUID(), TRIMESTER, level,
            pFail == null ? null : new BigDecimal(pFail), new BigDecimal("0.0100"), false, "{}",
            LocalDateTime.of(2026, 4, 10, 8, 0));
        return new StudentRisk(prediction, names, lastNames, subject);
    }

    private static Course course(UUID id, String gradeName, String parallelName) {
        return new Course(id, 5, gradeName, 2, parallelName, ACADEMIC_YEAR_ID, 2026,
            UUID.randomUUID(), "Ana Perez", true);
    }
}
