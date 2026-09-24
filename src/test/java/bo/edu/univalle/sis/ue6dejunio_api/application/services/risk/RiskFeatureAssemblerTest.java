package bo.edu.univalle.sis.ue6dejunio_api.application.services.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskFeatures;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskFeatureDomain.AttendanceRateRow;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskFeatureDomain.CriterionScoreRow;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Rows in, vectors out.
 *
 * <p>This is where what the model is told is actually decided, so it is checked without a database
 * and without HTTP: which dimension a mark lands in, that the order within a dimension survives,
 * and who does not get a vector at all.
 */
class RiskFeatureAssemblerTest {

    private static final int TRIMESTER = 1;
    private static final UUID MATH = UUID.randomUUID();
    private static final UUID LANGUAGE = UUID.randomUUID();
    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BRUNO = UUID.randomUUID();

    private static CriterionScoreRow row(UUID student, UUID group, String dimension, String score) {
        return new CriterionScoreRow(student, group, dimension, new BigDecimal(score));
    }

    private static Map<UUID, Integer> planned(UUID group, int count) {
        return Map.of(group, count);
    }

    @Test
    void assemble_sortsEachMarkIntoItsOwnDimension() {
        List<RiskFeatures> vectors =
                RiskFeatureAssembler.assemble(
                        TRIMESTER,
                        List.of(
                                row(ANA, MATH, "Being", "8"),
                                row(ANA, MATH, "Knowing", "30"),
                                row(ANA, MATH, "Doing", "25"),
                                row(ANA, MATH, "Deciding", "4")),
                        planned(MATH, 8),
                        List.of());

        assertThat(vectors)
                .singleElement()
                .satisfies(
                        vector -> {
                            assertThat(vector.being())
                                    .singleElement(BIG_DECIMAL)
                                    .isEqualByComparingTo("8");
                            assertThat(vector.knowing())
                                    .singleElement(BIG_DECIMAL)
                                    .isEqualByComparingTo("30");
                            assertThat(vector.doing())
                                    .singleElement(BIG_DECIMAL)
                                    .isEqualByComparingTo("25");
                            assertThat(vector.deciding())
                                    .singleElement(BIG_DECIMAL)
                                    .isEqualByComparingTo("4");
                            assertThat(vector.isComplete()).isTrue();
                        });
    }

    /** The trend feature is the last mark minus the first, so the sequence is part of the value. */
    @Test
    void assemble_keepsTheMarksInTheOrderTheyArrived() {
        List<RiskFeatures> vectors =
                RiskFeatureAssembler.assemble(
                        TRIMESTER,
                        List.of(
                                row(ANA, MATH, "Knowing", "10"),
                                row(ANA, MATH, "Knowing", "20"),
                                row(ANA, MATH, "Knowing", "45")),
                        planned(MATH, 8),
                        List.of());

        assertThat(vectors.get(0).knowing())
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("45"));
    }

    /** One vector per student per subject: the model judges a student in a subject, not overall. */
    @Test
    void assemble_keepsAStudentsSubjectsApart() {
        List<RiskFeatures> vectors =
                RiskFeatureAssembler.assemble(
                        TRIMESTER,
                        List.of(
                                row(ANA, MATH, "Knowing", "30"),
                                row(ANA, LANGUAGE, "Knowing", "40")),
                        Map.of(MATH, 8, LANGUAGE, 6),
                        List.of());

        assertThat(vectors).hasSize(2);
        assertThat(vectors)
                .extracting(RiskFeatures::classGroupId)
                .containsExactlyInAnyOrder(MATH, LANGUAGE);
        assertThat(vectors).extracting(RiskFeatures::studentId).containsOnly(ANA);
    }

    @Test
    void assemble_keepsStudentsApart() {
        List<RiskFeatures> vectors =
                RiskFeatureAssembler.assemble(
                        TRIMESTER,
                        List.of(row(ANA, MATH, "Knowing", "30"), row(BRUNO, MATH, "Knowing", "12")),
                        planned(MATH, 8),
                        List.of());

        assertThat(vectors).hasSize(2);
        assertThat(vectors)
                .extracting(RiskFeatures::studentId)
                .containsExactlyInAnyOrder(ANA, BRUNO);
    }

    @Test
    void assemble_attachesAttendanceToTheRightStudentAndSubject() {
        List<RiskFeatures> vectors =
                RiskFeatureAssembler.assemble(
                        TRIMESTER,
                        List.of(row(ANA, MATH, "Knowing", "30"), row(BRUNO, MATH, "Knowing", "12")),
                        planned(MATH, 8),
                        List.of(
                                new AttendanceRateRow(ANA, MATH, new BigDecimal("87.50")),
                                new AttendanceRateRow(BRUNO, MATH, new BigDecimal("41.00"))));

        assertThat(vectors)
                .filteredOn(v -> v.studentId().equals(ANA))
                .allSatisfy(v -> assertThat(v.attendancePct()).isEqualByComparingTo("87.50"));
        assertThat(vectors)
                .filteredOn(v -> v.studentId().equals(BRUNO))
                .allSatisfy(v -> assertThat(v.attendancePct()).isEqualByComparingTo("41.00"));
    }

    /**
     * Nobody has marked attendance yet. Left null rather than filled with a zero, because the model
     * treats a missing feature as missing and a zero as a student who attended nothing.
     */
    @Test
    void assemble_noAttendanceMarkedYet_leavesItUnstatedRatherThanZero() {
        List<RiskFeatures> vectors =
                RiskFeatureAssembler.assemble(
                        TRIMESTER,
                        List.of(row(ANA, MATH, "Knowing", "30")),
                        planned(MATH, 8),
                        List.of());

        assertThat(vectors.get(0).attendancePct()).isNull();
    }

    /**
     * Attendance for a student with no marks builds nothing: there is no vector to attach it to.
     */
    @Test
    void assemble_attendanceForAStudentWithNoMarks_buildsNothing() {
        List<RiskFeatures> vectors =
                RiskFeatureAssembler.assemble(
                        TRIMESTER,
                        List.of(),
                        planned(MATH, 8),
                        List.of(new AttendanceRateRow(ANA, MATH, new BigDecimal("90.00"))));

        assertThat(vectors).isEmpty();
    }

    /**
     * Progress is marks over what was planned, so with nothing planned there is no denominator and
     * the student cannot be predicted. Built anyway, carrying zero: dropped outright they would be
     * missing from both halves of the run summary, and a subject whose teacher entered marks
     * without planning criteria would report as though it did not exist.
     */
    @Test
    void assemble_aSubjectWithNothingPlanned_isBuiltButNotComplete() {
        List<RiskFeatures> vectors =
                RiskFeatureAssembler.assemble(
                        TRIMESTER,
                        List.of(
                                row(ANA, MATH, "Being", "8"),
                                row(ANA, MATH, "Knowing", "30"),
                                row(ANA, MATH, "Doing", "25"),
                                row(ANA, MATH, "Deciding", "4")),
                        Map.of(),
                        List.of());

        assertThat(vectors)
                .singleElement()
                .satisfies(
                        vector -> {
                            assertThat(vector.plannedCriteria()).isZero();
                            assertThat(vector.isComplete()).isFalse();
                        });
    }

    @Test
    void assemble_carriesThePlannedCountOfItsOwnSubject() {
        List<RiskFeatures> vectors =
                RiskFeatureAssembler.assemble(
                        TRIMESTER,
                        List.of(
                                row(ANA, MATH, "Knowing", "30"),
                                row(ANA, LANGUAGE, "Knowing", "40")),
                        Map.of(MATH, 8, LANGUAGE, 3),
                        List.of());

        assertThat(vectors)
                .filteredOn(v -> v.classGroupId().equals(MATH))
                .allSatisfy(v -> assertThat(v.plannedCriteria()).isEqualTo(8));
        assertThat(vectors)
                .filteredOn(v -> v.classGroupId().equals(LANGUAGE))
                .allSatisfy(v -> assertThat(v.plannedCriteria()).isEqualTo(3));
    }

    /**
     * Built, but not complete. The model answers 422 without a mark in every dimension, so the
     * caller has to be able to tell this apart and skip it before the batch is sent.
     */
    @Test
    void assemble_aStudentMissingADimension_isBuiltButNotComplete() {
        List<RiskFeatures> vectors =
                RiskFeatureAssembler.assemble(
                        TRIMESTER,
                        List.of(row(ANA, MATH, "Being", "8"), row(ANA, MATH, "Knowing", "30")),
                        planned(MATH, 8),
                        List.of());

        assertThat(vectors)
                .singleElement()
                .satisfies(vector -> assertThat(vector.isComplete()).isFalse());
    }

    /** A dimension the gradebook does not have is not a mark this side can place anywhere. */
    @Test
    void assemble_aDimensionNobodyRecognises_landsInNoList() {
        List<RiskFeatures> vectors =
                RiskFeatureAssembler.assemble(
                        TRIMESTER,
                        List.of(
                                row(ANA, MATH, "Knowing", "30"),
                                row(ANA, MATH, "Autoevaluacion", "5")),
                        planned(MATH, 8),
                        List.of());

        RiskFeatures vector = vectors.get(0);
        assertThat(vector.knowing()).hasSize(1);
        assertThat(vector.being()).isEmpty();
        assertThat(vector.doing()).isEmpty();
        assertThat(vector.deciding()).isEmpty();
    }

    /**
     * The same rows must produce the same batch in the same order. Reshuffled between two identical
     * runs, the boundary between one chunked request and the next moves, and a run that failed
     * cannot be reproduced.
     */
    @Test
    void assemble_theSameRowsTwice_produceTheSameOrder() {
        List<CriterionScoreRow> rows =
                List.of(
                        row(ANA, MATH, "Knowing", "30"),
                        row(BRUNO, MATH, "Knowing", "12"),
                        row(ANA, LANGUAGE, "Knowing", "40"));
        Map<UUID, Integer> counts = Map.of(MATH, 8, LANGUAGE, 6);

        List<RiskFeatures> first =
                RiskFeatureAssembler.assemble(TRIMESTER, rows, counts, List.of());
        List<RiskFeatures> second =
                RiskFeatureAssembler.assemble(TRIMESTER, rows, counts, List.of());

        assertThat(first).containsExactlyElementsOf(second);
    }

    @Test
    void assemble_carriesTheTrimesterItWasRunFor() {
        List<RiskFeatures> vectors =
                RiskFeatureAssembler.assemble(
                        3, List.of(row(ANA, MATH, "Knowing", "30")), planned(MATH, 8), List.of());

        assertThat(vectors.get(0).trimester()).isEqualTo(3);
    }

    @Test
    void assemble_nothingToWorkFrom_buildsNothing() {
        assertThat(RiskFeatureAssembler.assemble(TRIMESTER, List.of(), Map.of(), List.of()))
                .isEmpty();
    }
}
