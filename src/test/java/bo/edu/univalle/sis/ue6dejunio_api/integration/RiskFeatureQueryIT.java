package bo.edu.univalle.sis.ue6dejunio_api.integration;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskFeatureDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskFeatureDomain.AttendanceRateRow;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskFeatureDomain.CriterionScoreRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The four reads that feed the model, against a real Postgres.
 *
 * <p>Every one of them is hand-written SQL over column names and status words this code does not
 * own, and that is exactly what a mocked port cannot check. A misspelled column fails loudly the
 * first time it runs; a status word the table never stores fails silently forever, handing the
 * model a defensible-looking number that is wrong.
 *
 * <p>Trimester 1 throughout — it closed on 2026-05-31, so the "only up to today" bound never moves
 * the answer and the assertions do not expire.
 */
class RiskFeatureQueryIT extends AbstractIntegrationTest {

    @Autowired
    private IRiskFeatureDomain features;

    private static final int TRIMESTER = 1;
    private static final LocalDate DAY_ONE = LocalDate.of(2026, 3, 2);

    private UUID teacher;
    private UUID courseId;
    private UUID mathGroup;
    private UUID languageGroup;
    private UUID ana;
    private UUID anaEnrollment;

    @BeforeEach
    void seedSchool() {
        teacher = seedUser("Teacher", false);
        courseId = seedCourse(teacher, "A");
        mathGroup = seedClassGroup(courseId, teacher, "Matematicas");
        languageGroup = seedClassGroup(courseId, teacher, "Lenguaje");
        ana = seedStudent("Ana", "Alvarez");
        anaEnrollment = seedEnrollment(ana, courseId);
    }

    private void seedAttendance(UUID enrollmentId, UUID classGroupId, LocalDate date, String status) {
        jdbc.update(
            "INSERT INTO attendance (id_course_enrollment, id_class_group, date, status) "
                + "VALUES (?,?,?,?)",
            enrollmentId, classGroupId, java.sql.Date.valueOf(date), status);
    }

    // ---------------------------------------------------------------- active class groups

    /**
     * The column is {@code is_active}. Named {@code active} the statement does not return an empty
     * list, it fails to parse — and a sweep of the school never starts.
     */
    @Test
    void activeClassGroupIds_findsEverySubjectOfTheYear() {
        List<UUID> ids = features.activeClassGroupIds(currentAcademicYear());

        assertThat(ids).containsExactlyInAnyOrder(mathGroup, languageGroup);
    }

    @Test
    void activeClassGroupIds_leavesOutTheOnesTheSchoolClosed() {
        jdbc.update("UPDATE class_groups SET is_active = false WHERE id_class_group = ?", languageGroup);

        assertThat(features.activeClassGroupIds(currentAcademicYear())).containsExactly(mathGroup);
    }

    @Test
    void activeClassGroupIds_anotherGestionIsAnotherSchoolYear() {
        assertThat(features.activeClassGroupIds(2025)).isEmpty();
    }

    // ---------------------------------------------------------------- criterion scores

    @Test
    void criterionScores_bringsBackEveryMarkWithItsDimension() {
        UUID being = seedCriterion(mathGroup, TRIMESTER, "Being", "Puntualidad");
        UUID knowing = seedCriterion(mathGroup, TRIMESTER, "Knowing", "Prueba 1");
        seedCriterionScore(anaEnrollment, being, 8.0);
        seedCriterionScore(anaEnrollment, knowing, 30.0);

        List<CriterionScoreRow> rows = features.criterionScores(List.of(mathGroup), TRIMESTER);

        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.studentId()).isEqualTo(ana);
            assertThat(r.classGroupId()).isEqualTo(mathGroup);
        });
        assertThat(rows).extracting(CriterionScoreRow::dimension)
            .containsExactlyInAnyOrder("Being", "Knowing");
    }

    /**
     * The trend feature is the last mark minus the first, so the order the rows come back in is
     * part of the value. Two marks entered in the same second have to break their tie the same way
     * on every run, or the same student's trend changes sign between two sweeps that saw identical
     * data.
     */
    @Test
    void criterionScores_ordersMarksTheSameWayEveryTime() {
        UUID first = seedCriterion(mathGroup, TRIMESTER, "Knowing", "Prueba 1");
        UUID second = seedCriterion(mathGroup, TRIMESTER, "Knowing", "Prueba 2");
        UUID third = seedCriterion(mathGroup, TRIMESTER, "Knowing", "Prueba 3");
        seedCriterionScore(anaEnrollment, first, 10.0);
        seedCriterionScore(anaEnrollment, second, 20.0);
        seedCriterionScore(anaEnrollment, third, 30.0);
        // One clock reading for all three: the gradebook writes a whole sheet in one request, and
        // created_at has no sub-millisecond guarantee to separate them.
        jdbc.update("UPDATE assessment_scores SET created_at = TIMESTAMP '2026-03-10 09:00:00'");

        List<BigDecimal> firstRun = features.criterionScores(List.of(mathGroup), TRIMESTER)
            .stream().map(CriterionScoreRow::score).toList();
        List<BigDecimal> secondRun = features.criterionScores(List.of(mathGroup), TRIMESTER)
            .stream().map(CriterionScoreRow::score).toList();

        assertThat(firstRun).isEqualTo(secondRun);
    }

    /**
     * A criterion graded by activity is still one mark in its dimension — the average of its items,
     * which is the rule the gradebook itself applies. Reading only the direct rows would hand the
     * model nothing at all for a teacher who grades this way, and their students would never
     * complete a vector.
     */
    @Test
    void criterionScores_averagesAnActivitysItemsIntoOneMark() {
        UUID criterion = seedActivityCriterion(mathGroup, TRIMESTER, "Doing", "Trabajos", "Tarea");
        seedEventScore(anaEnrollment, seedEvent(criterion, "Tarea 1"), 10.0);
        seedEventScore(anaEnrollment, seedEvent(criterion, "Tarea 2"), 20.0);

        List<CriterionScoreRow> rows = features.criterionScores(List.of(mathGroup), TRIMESTER);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).dimension()).isEqualTo("Doing");
        assertThat(rows.get(0).score()).isEqualByComparingTo("15.00");
    }

    /**
     * Ten items under one criterion are one mark, not ten. Left as ten, a criterion somebody split
     * into homework outweighs one scored directly by ten to one, and the dimension's spread becomes
     * a fact about how the teacher organised their sheet.
     */
    @Test
    void criterionScores_oneRowPerCriterionNoMatterHowItWasGraded() {
        UUID direct = seedCriterion(mathGroup, TRIMESTER, "Doing", "Exposicion");
        seedCriterionScore(anaEnrollment, direct, 30.0);
        UUID byActivity = seedActivityCriterion(mathGroup, TRIMESTER, "Doing", "Trabajos", "Tarea");
        seedEventScore(anaEnrollment, seedEvent(byActivity, "Tarea 1"), 10.0);
        seedEventScore(anaEnrollment, seedEvent(byActivity, "Tarea 2"), 20.0);
        seedEventScore(anaEnrollment, seedEvent(byActivity, "Tarea 3"), 30.0);

        List<CriterionScoreRow> rows = features.criterionScores(List.of(mathGroup), TRIMESTER);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(CriterionScoreRow::score)
            .usingElementComparator(BigDecimal::compareTo)
            .containsExactlyInAnyOrder(new BigDecimal("30.00"), new BigDecimal("20.00"));
    }

    @Test
    void criterionScores_leavesOutAnotherTrimester() {
        UUID other = seedCriterion(mathGroup, 2, "Knowing", "Prueba de otro trimestre");
        seedCriterionScore(anaEnrollment, other, 40.0);

        assertThat(features.criterionScores(List.of(mathGroup), TRIMESTER)).isEmpty();
    }

    /**
     * A student the school took off the roll still has their marks in the table. Predicting them
     * spends a slot in a bounded batch on somebody who is not coming back, and puts a stranger at
     * the top of the teacher's risk list.
     */
    @Test
    void criterionScores_leavesOutAStudentNoLongerOnTheRoll() {
        UUID criterion = seedCriterion(mathGroup, TRIMESTER, "Knowing", "Prueba 1");
        seedCriterionScore(anaEnrollment, criterion, 30.0);
        jdbc.update("UPDATE course_enrollments SET status = 'Withdrawn' WHERE id_course_enrollment = ?",
            anaEnrollment);

        assertThat(features.criterionScores(List.of(mathGroup), TRIMESTER)).isEmpty();
    }

    @Test
    void criterionScores_noClassGroups_asksTheDatabaseNothing() {
        assertThat(features.criterionScores(List.of(), TRIMESTER)).isEmpty();
    }

    // ---------------------------------------------------------------- planned criteria

    @Test
    void plannedCriteriaCount_countsWhatTheTeacherPlannedInEachSubject() {
        seedCriterion(mathGroup, TRIMESTER, "Being", "Puntualidad");
        seedCriterion(mathGroup, TRIMESTER, "Knowing", "Prueba 1");
        seedCriterion(languageGroup, TRIMESTER, "Doing", "Lectura");
        seedCriterion(mathGroup, 2, "Knowing", "Otro trimestre");

        Map<UUID, Integer> counts = features.plannedCriteriaCount(
            List.of(mathGroup, languageGroup), TRIMESTER);

        assertThat(counts).containsOnly(
            org.assertj.core.api.Assertions.entry(mathGroup, 2),
            org.assertj.core.api.Assertions.entry(languageGroup, 1));
    }

    /** Absent, not zero: nothing planned is nothing to divide by, and the caller has to see that. */
    @Test
    void plannedCriteriaCount_aSubjectWithNothingPlannedIsAbsent() {
        seedCriterion(mathGroup, TRIMESTER, "Being", "Puntualidad");

        assertThat(features.plannedCriteriaCount(List.of(mathGroup, languageGroup), TRIMESTER))
            .containsOnlyKeys(mathGroup);
    }

    // ---------------------------------------------------------------- attendance

    /**
     * The statuses are the four the column's CHECK allows, in English. Asking for Spanish ones
     * returns rows and scores every single day absent — a defensible-looking 0% for a student who
     * never missed a class, and nothing anywhere says the query was wrong.
     *
     * <p>The rule is the model's, not the attendance panel's: the training set counted a day as
     * attended for any mark that was not an outright absence. Only the {@code Absent} day counts
     * against here, so three of four is 75%. The panel's own figure for the same student is 50% —
     * they answer different questions, and this one has to match what the model was trained on.
     */
    @Test
    void attendanceRates_onlyAnOutrightAbsenceCountsAgainst() {
        seedAttendance(anaEnrollment, mathGroup, DAY_ONE, "Present");
        seedAttendance(anaEnrollment, mathGroup, DAY_ONE.plusDays(1), "Late");
        seedAttendance(anaEnrollment, mathGroup, DAY_ONE.plusDays(2), "Absent");
        seedAttendance(anaEnrollment, mathGroup, DAY_ONE.plusDays(3), "Excused");

        List<AttendanceRateRow> rates = features.attendanceRates(List.of(mathGroup), TRIMESTER);

        assertThat(rates).hasSize(1);
        assertThat(rates.get(0).studentId()).isEqualTo(ana);
        assertThat(rates.get(0).classGroupId()).isEqualTo(mathGroup);
        assertThat(rates.get(0).attendancePct()).isEqualByComparingTo("75.00");
    }

    /**
     * The one divergence worth stating outright, because it is the one a future reader will try to
     * "fix": a day off with leave is not a day missed, for the model. The attendance panel drops
     * it from the calculation entirely; the training set counted it as attended.
     */
    @Test
    void attendanceRates_aDayOffWithLeaveCountsAsAttended() {
        seedAttendance(anaEnrollment, mathGroup, DAY_ONE, "Excused");
        seedAttendance(anaEnrollment, mathGroup, DAY_ONE.plusDays(1), "Absent");

        assertThat(features.attendanceRates(List.of(mathGroup), TRIMESTER).get(0).attendancePct())
            .isEqualByComparingTo("50.00");
    }

    /** Marked at course level, the day counts for every subject of that course. */
    @Test
    void attendanceRates_theCourseWideRollCoversEverySubject() {
        seedAttendance(anaEnrollment, null, DAY_ONE, "Present");
        seedAttendance(anaEnrollment, null, DAY_ONE.plusDays(1), "Absent");

        List<AttendanceRateRow> rates = features.attendanceRates(
            List.of(mathGroup, languageGroup), TRIMESTER);

        assertThat(rates).hasSize(2);
        assertThat(rates).extracting(AttendanceRateRow::classGroupId)
            .containsExactlyInAnyOrder(mathGroup, languageGroup);
        assertThat(rates).allSatisfy(r ->
            assertThat(r.attendancePct()).isEqualByComparingTo("50.00"));
    }

    /**
     * A fallback, not a sum. The teacher who marks their own subject produces a session row on a
     * day the course-wide roll already covers, and counting both makes one day two — a student
     * present in the subject and absent from the course-wide roll lands at 50% for a day they
     * attended.
     */
    @Test
    void attendanceRates_theSubjectsOwnMarkWinsOverTheCourseWideRoll() {
        seedAttendance(anaEnrollment, null, DAY_ONE, "Absent");
        seedAttendance(anaEnrollment, mathGroup, DAY_ONE, "Present");

        List<AttendanceRateRow> rates = features.attendanceRates(List.of(mathGroup), TRIMESTER);

        assertThat(rates).hasSize(1);
        assertThat(rates.get(0).attendancePct()).isEqualByComparingTo("100.00");
    }

    /** A subject that marks its own days does not inherit the course's other days. */
    @Test
    void attendanceRates_mixesTheTwoSourcesDayByDay() {
        seedAttendance(anaEnrollment, null, DAY_ONE, "Present");
        seedAttendance(anaEnrollment, null, DAY_ONE.plusDays(1), "Present");
        seedAttendance(anaEnrollment, mathGroup, DAY_ONE.plusDays(1), "Absent");

        List<AttendanceRateRow> rates = features.attendanceRates(List.of(mathGroup), TRIMESTER);

        assertThat(rates.get(0).attendancePct()).isEqualByComparingTo("50.00");
    }

    @Test
    void attendanceRates_leavesOutDaysOutsideTheTrimester() {
        seedAttendance(anaEnrollment, mathGroup, DAY_ONE, "Present");
        seedAttendance(anaEnrollment, mathGroup, LocalDate.of(2026, 7, 1), "Absent");

        assertThat(features.attendanceRates(List.of(mathGroup), TRIMESTER).get(0).attendancePct())
            .isEqualByComparingTo("100.00");
    }

    /** Nobody marked yet is not 0% attendance. Absent from the answer, so no vector claims it. */
    @Test
    void attendanceRates_aSubjectNobodyHasMarkedIsAbsent() {
        assertThat(features.attendanceRates(List.of(mathGroup), TRIMESTER)).isEmpty();
    }

    @Test
    void attendanceRates_keepsStudentsApart() {
        UUID bruno = seedStudent("Bruno", "Bermudez");
        UUID brunoEnrollment = seedEnrollment(bruno, courseId);
        seedAttendance(anaEnrollment, mathGroup, DAY_ONE, "Present");
        seedAttendance(brunoEnrollment, mathGroup, DAY_ONE, "Absent");

        List<AttendanceRateRow> rates = features.attendanceRates(List.of(mathGroup), TRIMESTER);

        assertThat(rates).hasSize(2);
        assertThat(rates).filteredOn(r -> r.studentId().equals(ana))
            .allSatisfy(r -> assertThat(r.attendancePct()).isEqualByComparingTo("100.00"));
        assertThat(rates).filteredOn(r -> r.studentId().equals(bruno))
            .allSatisfy(r -> assertThat(r.attendancePct()).isEqualByComparingTo("0.00"));
    }

    private int currentAcademicYear() {
        return jdbc.queryForObject(
            "SELECT year FROM academic_years ORDER BY year DESC LIMIT 1", Integer.class);
    }
}
