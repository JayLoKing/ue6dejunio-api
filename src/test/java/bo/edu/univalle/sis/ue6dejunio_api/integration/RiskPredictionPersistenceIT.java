package bo.edu.univalle.sis.ue6dejunio_api.integration;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.NewRiskPrediction;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskFeatures;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskLevel;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskPrediction;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.StudentRisk;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskPredictionDomain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mapping, against a real Postgres.
 *
 * <p>Unit tests here would prove nothing: the port is mocked in every service test, and the three
 * things that can actually break live below it — the jsonb column, the unique constraint that makes
 * this an upsert, and the CASE that orders the four risk levels by severity rather than
 * alphabetically.
 */
class RiskPredictionPersistenceIT extends AbstractIntegrationTest {

    @Autowired
    private IRiskPredictionDomain riskPredictions;

    private UUID courseId;
    private UUID mathGroup;
    private UUID languageGroup;
    private UUID ana;
    private UUID bruno;

    /**
     * A vector with something recognisable in every dimension, so the assertion about the jsonb
     * column can point at a value and say where it came from.
     */
    private static final RiskFeatures FEATURES = new RiskFeatures(
        UUID.randomUUID(), UUID.randomUUID(), 1,
        List.of(new BigDecimal("8")),
        List.of(new BigDecimal("32.5"), new BigDecimal("41")),
        List.of(new BigDecimal("28")),
        List.of(new BigDecimal("4")),
        new BigDecimal("87.5"), 8);

    @BeforeEach
    void seedSchool() {
        UUID teacher = seedUser("Teacher", false);
        courseId = seedCourse(teacher, "A");
        mathGroup = seedClassGroup(courseId, teacher, "Matematicas");
        languageGroup = seedClassGroup(courseId, teacher, "Lenguaje");
        ana = seedStudent("Ana", "Alvarez");
        bruno = seedStudent("Bruno", "Bermudez");
        seedEnrollment(ana, courseId);
        seedEnrollment(bruno, courseId);
    }

    private NewRiskPrediction prediction(UUID student, UUID classGroup, RiskLevel level,
                                      String pFail, String pOutstanding) {
        return new NewRiskPrediction(student, classGroup, 1, level,
            new BigDecimal(pFail), new BigDecimal(pOutstanding), FEATURES,
            LocalDateTime.of(2026, 4, 10, 8, 0));
    }

    @Test
    void upsertAll_firstRun_writesEveryFieldBackIntact() {
        List<IRiskPredictionDomain.UpsertResult> results = riskPredictions.upsertAll(List.of(
            prediction(ana, mathGroup, RiskLevel.RIESGO_CRITICO, "0.7412", "0.0102")));

        assertThat(results).hasSize(1);
        IRiskPredictionDomain.UpsertResult result = results.get(0);
        assertThat(result.previousLevel()).isNull();
        assertThat(result.levelChanged()).isTrue();

        RiskPrediction stored = result.stored();
        assertThat(stored.id()).isNotNull();
        assertThat(stored.studentId()).isEqualTo(ana);
        assertThat(stored.classGroupId()).isEqualTo(mathGroup);
        assertThat(stored.trimester()).isEqualTo(1);
        assertThat(stored.riskLevel()).isEqualTo(RiskLevel.RIESGO_CRITICO);
        assertThat(stored.pFail()).isEqualByComparingTo("0.7412");
        assertThat(stored.pOutstanding()).isEqualByComparingTo("0.0102");
        assertThat(stored.attended()).isFalse();
        assertThat(stored.predictedAt()).isEqualTo(LocalDateTime.of(2026, 4, 10, 8, 0));
    }

    /**
     * The column is jsonb, and Hibernate sends a text parameter unless told otherwise. Without
     * {@code SqlTypes.JSON} this write fails outright — which is exactly the kind of break a mocked
     * port never sees.
     */
    @Test
    void upsertAll_featureVector_survivesTheJsonbColumn() {
        riskPredictions.upsertAll(List.of(
            prediction(ana, mathGroup, RiskLevel.EN_RIESGO, "0.1200", "0.0500")));

        String storedJson = jdbc.queryForObject(
            "SELECT features_analyzed::text FROM risk_predictions WHERE id_student = ?",
            String.class, ana);

        assertThat(storedJson).contains("\"knowing\"").contains("32.5");
    }

    /**
     * The read the announcement side makes: the rows it just wrote, named, in one query. Without it
     * a run resolves the names one subject at a time inside a loop.
     */
    @Test
    void byIds_namesExactlyTheRowsItWasAskedFor() {
        List<UUID> ids = riskPredictions.upsertAll(List.of(
            prediction(ana, mathGroup, RiskLevel.RIESGO_CRITICO, "0.8000", "0.0100"),
            prediction(bruno, languageGroup, RiskLevel.SIN_RIESGO, "0.0200", "0.1000")))
            .stream().map(result -> result.stored().id()).toList();

        List<StudentRisk> named = riskPredictions.byIds(List.of(ids.get(0)));

        assertThat(named).singleElement().satisfies(risk -> {
            assertThat(risk.studentFullName()).isEqualTo("Alvarez Ana");
            assertThat(risk.subjectName()).isEqualTo("Matematicas");
            assertThat(risk.prediction().riskLevel()).isEqualTo(RiskLevel.RIESGO_CRITICO);
        });
    }

    @Test
    void byIds_nothingAskedFor_readsNothing() {
        assertThat(riskPredictions.byIds(List.of())).isEmpty();
    }

    /**
     * The whole reason the table gained {@code id_class_group}: nine subjects for one student are
     * nine rows, not one that survives.
     */
    @Test
    void upsertAll_sameStudentTwoSubjects_keepsBothRows() {
        riskPredictions.upsertAll(List.of(
            prediction(ana, mathGroup, RiskLevel.RIESGO_CRITICO, "0.8000", "0.0100"),
            prediction(ana, languageGroup, RiskLevel.SOBRESALIENTE, "0.0100", "0.9000")));

        Integer rows = jdbc.queryForObject(
            "SELECT COUNT(*) FROM risk_predictions WHERE id_student = ?", Integer.class, ana);

        assertThat(rows).isEqualTo(2);
    }

    @Test
    void upsertAll_secondRun_correctsTheStandingRowAndReportsWhatItHeld() {
        riskPredictions.upsertAll(List.of(
            prediction(ana, mathGroup, RiskLevel.EN_RIESGO, "0.2000", "0.0300")));

        List<IRiskPredictionDomain.UpsertResult> second = riskPredictions.upsertAll(List.of(
            prediction(ana, mathGroup, RiskLevel.RIESGO_CRITICO, "0.6600", "0.0050")));

        assertThat(second.get(0).previousLevel()).isEqualTo(RiskLevel.EN_RIESGO);
        assertThat(second.get(0).stored().riskLevel()).isEqualTo(RiskLevel.RIESGO_CRITICO);
        assertThat(second.get(0).levelChanged()).isTrue();

        Integer rows = jdbc.queryForObject(
            "SELECT COUNT(*) FROM risk_predictions WHERE id_student = ? AND id_class_group = ?",
            Integer.class, ana, mathGroup);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void upsertAll_sameLevelAgain_reportsNoChange() {
        riskPredictions.upsertAll(List.of(
            prediction(ana, mathGroup, RiskLevel.EN_RIESGO, "0.2000", "0.0300")));

        List<IRiskPredictionDomain.UpsertResult> second = riskPredictions.upsertAll(List.of(
            prediction(ana, mathGroup, RiskLevel.EN_RIESGO, "0.3100", "0.0200")));

        assertThat(second.get(0).levelChanged()).isFalse();
        assertThat(second.get(0).stored().pFail()).isEqualByComparingTo("0.3100");
    }

    /** What the Director marked as handled must outlive the next sweep, or it is marked forever. */
    @Test
    void upsertAll_rePredicting_keepsWhatSomebodyAlreadyAttended() {
        RiskPrediction first = riskPredictions.upsertAll(List.of(
            prediction(ana, mathGroup, RiskLevel.RIESGO_CRITICO, "0.7000", "0.0100")))
            .get(0).stored();
        riskPredictions.markAttended(first.id(), true);

        RiskPrediction rePredicted = riskPredictions.upsertAll(List.of(
            prediction(ana, mathGroup, RiskLevel.RIESGO_CRITICO, "0.7500", "0.0100")))
            .get(0).stored();

        assertThat(rePredicted.attended()).isTrue();
    }

    @Test
    void upsertAll_aWholeRun_returnsResultsInTheOrderItWasGiven() {
        List<IRiskPredictionDomain.UpsertResult> results = riskPredictions.upsertAll(List.of(
            prediction(bruno, languageGroup, RiskLevel.SIN_RIESGO, "0.0400", "0.1000"),
            prediction(ana, mathGroup, RiskLevel.RIESGO_CRITICO, "0.7000", "0.0100"),
            prediction(ana, languageGroup, RiskLevel.SOBRESALIENTE, "0.0100", "0.8800")));

        assertThat(results).extracting(r -> r.stored().studentId())
            .containsExactly(bruno, ana, ana);
        assertThat(results).extracting(r -> r.stored().classGroupId())
            .containsExactly(languageGroup, mathGroup, languageGroup);
    }

    /**
     * Ordered by severity, not by the text of the level. Sorted alphabetically {@code EnRiesgo}
     * comes before {@code RiesgoCritico}, and the list would open on the students who are fine.
     */
    @Test
    void byClassGroupAndTrimester_ordersTheFailingStudentsFirst() {
        UUID carla = seedStudent("Carla", "Castro");
        seedEnrollment(carla, courseId);
        riskPredictions.upsertAll(List.of(
            prediction(ana, mathGroup, RiskLevel.SOBRESALIENTE, "0.0100", "0.9000"),
            prediction(bruno, mathGroup, RiskLevel.EN_RIESGO, "0.2000", "0.0300"),
            prediction(carla, mathGroup, RiskLevel.RIESGO_CRITICO, "0.8100", "0.0050")));

        List<StudentRisk> list = riskPredictions.byClassGroupAndTrimester(mathGroup, 1);

        assertThat(list).extracting(r -> r.prediction().riskLevel())
            .containsExactly(RiskLevel.RIESGO_CRITICO, RiskLevel.EN_RIESGO, RiskLevel.SOBRESALIENTE);
        assertThat(list).extracting(StudentRisk::studentFullName)
            .containsExactly("Castro Carla", "Bermudez Bruno", "Alvarez Ana");
        assertThat(list).extracting(StudentRisk::subjectName)
            .containsOnly("Matematicas");
    }

    @Test
    void byCourseAndTrimester_spansEverySubjectAndNamesThem() {
        riskPredictions.upsertAll(List.of(
            prediction(ana, mathGroup, RiskLevel.RIESGO_CRITICO, "0.8000", "0.0100"),
            prediction(ana, languageGroup, RiskLevel.SIN_RIESGO, "0.0200", "0.1000")));

        List<StudentRisk> list = riskPredictions.byCourseAndTrimester(courseId, 1);

        assertThat(list).extracting(StudentRisk::subjectName)
            .containsExactly("Matematicas", "Lenguaje");
    }

    @Test
    void byStudent_returnsEverySubjectTheStudentSits() {
        riskPredictions.upsertAll(List.of(
            prediction(ana, mathGroup, RiskLevel.EN_RIESGO, "0.2000", "0.0300"),
            prediction(ana, languageGroup, RiskLevel.SOBRESALIENTE, "0.0100", "0.9000"),
            prediction(bruno, mathGroup, RiskLevel.RIESGO_CRITICO, "0.9000", "0.0010")));

        List<StudentRisk> list = riskPredictions.byStudent(ana);

        assertThat(list).hasSize(2);
        assertThat(list).extracting(r -> r.prediction().studentId()).containsOnly(ana);
    }

    @Test
    void markAttended_recordsThatSomebodyActedOnIt() {
        UUID id = riskPredictions.upsertAll(List.of(
            prediction(ana, mathGroup, RiskLevel.RIESGO_CRITICO, "0.7000", "0.0100")))
            .get(0).stored().id();

        RiskPrediction attended = riskPredictions.markAttended(id, true);

        assertThat(attended.attended()).isTrue();
        assertThat(riskPredictions.findById(id)).get()
            .extracting(RiskPrediction::attended).isEqualTo(true);
    }

    @Test
    void upsertAll_nothingToWrite_touchesNothing() {
        assertThat(riskPredictions.upsertAll(List.of())).isEmpty();
    }

    // ------------------------------------------------- the once-a-day announcement bound

    /**
     * A prediction nobody has announced yet is claimable, and claiming it stamps the row.
     *
     * <p>A mocked port proves none of this. The claim is a bulk {@code UPDATE} followed by a read
     * of what it wrote, and both halves live in the database: whether the stamp actually lands,
     * whether the persistence context still holds the old value afterwards, and whether the second
     * caller of the day correctly gets nothing.
     */
    @Test
    void claimForNotification_firstTimeToday_isGrantedAndStamped() {
        UUID id = riskPredictions.upsertAll(List.of(
            prediction(ana, mathGroup, RiskLevel.RIESGO_CRITICO, "0.8100", "0.0000")))
            .get(0).stored().id();
        LocalDateTime now = LocalDateTime.of(2026, 4, 10, 9, 30);

        Set<UUID> claimed = riskPredictions.claimForNotification(
            List.of(id), now, now.toLocalDate().atStartOfDay());

        assertThat(claimed).containsExactly(id);
        assertThat(jdbc.queryForObject(
            "SELECT last_notified_at FROM risk_predictions WHERE id_risk_prediction = ?",
            LocalDateTime.class, id)).isEqualTo(now);
    }

    /** The whole point: the same student in the same subject is not announced twice in a day. */
    @Test
    void claimForNotification_secondTimeSameDay_isRefused() {
        UUID id = riskPredictions.upsertAll(List.of(
            prediction(ana, mathGroup, RiskLevel.RIESGO_CRITICO, "0.8100", "0.0000")))
            .get(0).stored().id();
        LocalDateTime morning = LocalDateTime.of(2026, 4, 10, 9, 30);
        LocalDateTime startOfDay = morning.toLocalDate().atStartOfDay();
        riskPredictions.claimForNotification(List.of(id), morning, startOfDay);

        Set<UUID> second = riskPredictions.claimForNotification(
            List.of(id), morning.plusHours(3), startOfDay);

        assertThat(second).isEmpty();
        // And the first claim's instant survives: a refused claim must not move the stamp, or the
        // bound would slide forward for as long as the teacher keeps editing.
        assertThat(jdbc.queryForObject(
            "SELECT last_notified_at FROM risk_predictions WHERE id_risk_prediction = ?",
            LocalDateTime.class, id)).isEqualTo(morning);
    }

    /** It is a daily bound, not a permanent one: tomorrow the same student may be announced again. */
    @Test
    void claimForNotification_theNextDay_isGrantedAgain() {
        UUID id = riskPredictions.upsertAll(List.of(
            prediction(ana, mathGroup, RiskLevel.RIESGO_CRITICO, "0.8100", "0.0000")))
            .get(0).stored().id();
        LocalDateTime today = LocalDateTime.of(2026, 4, 10, 9, 30);
        riskPredictions.claimForNotification(
            List.of(id), today, today.toLocalDate().atStartOfDay());
        LocalDateTime tomorrow = today.plusDays(1);

        Set<UUID> next = riskPredictions.claimForNotification(
            List.of(id), tomorrow, tomorrow.toLocalDate().atStartOfDay());

        assertThat(next).containsExactly(id);
    }

    /** One student's bound must not silence another's, nor one subject another's. */
    @Test
    void claimForNotification_grantsOnlyTheRowsThatHaveNotSpokenToday() {
        List<IRiskPredictionDomain.UpsertResult> rows = riskPredictions.upsertAll(List.of(
            prediction(ana, mathGroup, RiskLevel.RIESGO_CRITICO, "0.8100", "0.0000"),
            prediction(bruno, languageGroup, RiskLevel.RIESGO_CRITICO, "0.7700", "0.0000")));
        UUID anaInMath = rows.get(0).stored().id();
        UUID brunoInLanguage = rows.get(1).stored().id();
        LocalDateTime morning = LocalDateTime.of(2026, 4, 10, 9, 30);
        LocalDateTime startOfDay = morning.toLocalDate().atStartOfDay();
        riskPredictions.claimForNotification(List.of(anaInMath), morning, startOfDay);

        Set<UUID> claimed = riskPredictions.claimForNotification(
            List.of(anaInMath, brunoInLanguage), morning.plusHours(1), startOfDay);

        assertThat(claimed).containsExactly(brunoInLanguage);
    }

    @Test
    void claimForNotification_nothingOffered_claimsNothing() {
        assertThat(riskPredictions.claimForNotification(
            List.of(), LocalDateTime.now(), LocalDateTime.now())).isEmpty();
    }
}
