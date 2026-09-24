package bo.edu.univalle.sis.ue6dejunio_api.application.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.risk.RiskPredictionService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.RiskModelUnavailableException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.SendNotificationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.NewRiskPrediction;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskLevel;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskPrediction;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.StudentRisk;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskFeatureDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskFeatureDomain.CriterionScoreRow;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskModelClient;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskPredictionDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskPredictionDomain.UpsertResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskPredictionService.RunSummary;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * What a run does with what it is given.
 *
 * <p>Everything below the service is mocked, so what these check is the sequencing: who is skipped
 * before the model is asked, that a score stays attached to the student it was computed for, and
 * which movements are worth writing to a teacher about. The SQL and the mapping have their own
 * tests against a real database, because nothing mocked can fail the way those do.
 */
@ExtendWith(MockitoExtension.class)
class RiskPredictionServiceTest {

    @Mock private IRiskFeatureDomain featureDomain;
    @Mock private IRiskModelClient modelClient;
    @Mock private IRiskPredictionDomain predictionDomain;
    @Mock private INotificationService notifications;
    @Mock private IClassGroupDomain classGroupDomain;
    @Mock private ICourseService courseService;

    private RiskPredictionService service;

    private static final int TRIMESTER = 1;
    private static final int YEAR = 2026;

    private final UUID mathGroup = UUID.randomUUID();
    private final UUID languageGroup = UUID.randomUUID();
    private final UUID teacher = UUID.randomUUID();
    private final UUID ana = UUID.randomUUID();
    private final UUID bruno = UUID.randomUUID();

    @BeforeEach
    void buildService() {
        service =
                new RiskPredictionService(
                        featureDomain,
                        modelClient,
                        predictionDomain,
                        notifications,
                        classGroupDomain,
                        courseService);
    }

    // ---------------------------------------------------------------- fixtures

    private CriterionScoreRow score(UUID student, UUID group, String dimension, String value) {
        return new CriterionScoreRow(student, group, dimension, new BigDecimal(value));
    }

    /** One mark in each of the four dimensions: the least the model will accept. */
    private List<CriterionScoreRow> completeMarks(UUID student, UUID group) {
        return List.of(
                score(student, group, "Being", "8"),
                score(student, group, "Knowing", "30"),
                score(student, group, "Doing", "25"),
                score(student, group, "Deciding", "4"));
    }

    private void givenMarks(List<CriterionScoreRow> marks, UUID... groups) {
        when(featureDomain.criterionScores(any(), anyInt())).thenReturn(marks);
        Map<UUID, Integer> planned = new HashMap<>();
        for (UUID group : groups) {
            planned.put(group, 8);
        }
        when(featureDomain.plannedCriteriaCount(any(), anyInt())).thenReturn(planned);
        when(featureDomain.attendanceRates(any(), anyInt())).thenReturn(List.of());
    }

    private RiskPrediction stored(UUID student, UUID group, RiskLevel level) {
        return new RiskPrediction(
                UUID.randomUUID(),
                student,
                group,
                TRIMESTER,
                level,
                new BigDecimal("0.7000"),
                new BigDecimal("0.0100"),
                false,
                "{}",
                LocalDateTime.of(2026, 4, 1, 9, 0));
    }

    private ClassGroup classGroup(UUID id, UUID teacherId) {
        return new ClassGroup(
                id,
                UUID.randomUUID(),
                "3ro",
                "A",
                UUID.randomUUID(),
                "Matematicas",
                teacherId,
                "Prof. Quispe",
                true);
    }

    /**
     * Wires the reads the announcement side makes, for the given standing rows.
     *
     * <p>Includes the once-a-day claim, granted here for everything asked. The bound itself is what
     * {@code aStudentAlreadyAnnouncedToday_isNotAnnouncedAgain} is for; every other test is about
     * what gets announced when nothing is holding it back.
     */
    private void givenAnnouncementLookups(ClassGroup subject, StudentRisk... rows) {
        when(classGroupDomain.findByIdIn(anyCollection())).thenReturn(List.of(subject));
        when(predictionDomain.byIds(anyCollection())).thenReturn(List.of(rows));
        grantTheDailyClaim();
    }

    /** Every prediction offered is claimable: nothing was announced earlier today. */
    private void grantTheDailyClaim() {
        when(predictionDomain.claimForNotification(anyCollection(), any(), any()))
                .thenAnswer(call -> Set.copyOf(call.getArgument(0, Collection.class)));
    }

    // ---------------------------------------------------------------- nothing to do

    @Test
    void predictYear_noActiveClassGroups_asksTheModelNothing() {
        when(featureDomain.activeClassGroupIds(YEAR)).thenReturn(List.of());

        RunSummary summary = service.predictYear(YEAR, TRIMESTER);

        assertThat(summary).isEqualTo(RunSummary.empty());
        verifyNoInteractions(modelClient, predictionDomain, notifications);
    }

    /**
     * The normal state of a subject early in a trimester. The model answers 422 for a vector with a
     * dimension missing, and a rejected batch costs every vector in it — so an incomplete student
     * is skipped here, quietly, and counted rather than turned into a failed call.
     */
    @Test
    void predictClassGroup_studentMissingADimension_isSkippedBeforeTheModelIsAsked() {
        givenMarks(
                List.of(
                        score(ana, mathGroup, "Being", "8"),
                        score(ana, mathGroup, "Knowing", "30")),
                mathGroup);

        RunSummary summary = service.predictClassGroup(mathGroup, TRIMESTER);

        assertThat(summary.considered()).isEqualTo(1);
        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.predicted()).isZero();
        verifyNoInteractions(modelClient, predictionDomain);
    }

    @Test
    void predictClassGroup_countsTheSkippedApartFromThePredicted() {
        List<CriterionScoreRow> marks = new ArrayList<>(completeMarks(ana, mathGroup));
        marks.add(score(bruno, mathGroup, "Being", "7"));
        givenMarks(marks, mathGroup);
        when(modelClient.predictBatch(anyList()))
                .thenReturn(
                        List.of(
                                new RiskScore(
                                        RiskLevel.SIN_RIESGO,
                                        new BigDecimal("0.02"),
                                        new BigDecimal("0.1"))));
        when(predictionDomain.upsertAll(anyList()))
                .thenReturn(
                        List.of(
                                new UpsertResult(
                                        stored(ana, mathGroup, RiskLevel.SIN_RIESGO),
                                        RiskLevel.SIN_RIESGO)));

        RunSummary summary = service.predictClassGroup(mathGroup, TRIMESTER);

        assertThat(summary.considered()).isEqualTo(2);
        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.predicted()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- what gets written

    @Test
    void predictClassGroup_writesTheModelsAnswerAgainstTheStudentItWasComputedFor() {
        givenMarks(completeMarks(ana, mathGroup), mathGroup);
        when(modelClient.predictBatch(anyList()))
                .thenReturn(
                        List.of(
                                new RiskScore(
                                        RiskLevel.RIESGO_CRITICO,
                                        new BigDecimal("0.8123"),
                                        new BigDecimal("0.0044"))));
        when(predictionDomain.upsertAll(anyList()))
                .thenReturn(
                        List.of(
                                new UpsertResult(
                                        stored(ana, mathGroup, RiskLevel.RIESGO_CRITICO),
                                        RiskLevel.RIESGO_CRITICO)));

        service.predictClassGroup(mathGroup, TRIMESTER);

        NewRiskPrediction written = captureWrite().get(0);
        assertThat(written.studentId()).isEqualTo(ana);
        assertThat(written.classGroupId()).isEqualTo(mathGroup);
        assertThat(written.trimester()).isEqualTo(TRIMESTER);
        assertThat(written.riskLevel()).isEqualTo(RiskLevel.RIESGO_CRITICO);
        assertThat(written.pFail()).isEqualByComparingTo("0.8123");
        assertThat(written.pOutstanding()).isEqualByComparingTo("0.0044");
    }

    /**
     * The column is {@code NOT NULL} and its default only applies when the column is left out of
     * the statement — which JPA never does. A null here is not a timestamp the database fills in
     * later, it is a write that fails.
     */
    @Test
    void predictClassGroup_stampsEveryRowWithWhenTheRunHappened() {
        givenMarks(completeMarks(ana, mathGroup), mathGroup);
        when(modelClient.predictBatch(anyList()))
                .thenReturn(
                        List.of(
                                new RiskScore(
                                        RiskLevel.SIN_RIESGO,
                                        new BigDecimal("0.02"),
                                        new BigDecimal("0.10"))));
        when(predictionDomain.upsertAll(anyList()))
                .thenReturn(
                        List.of(
                                new UpsertResult(
                                        stored(ana, mathGroup, RiskLevel.SIN_RIESGO),
                                        RiskLevel.SIN_RIESGO)));

        service.predictClassGroup(mathGroup, TRIMESTER);

        assertThat(captureWrite())
                .allSatisfy(prediction -> assertThat(prediction.predictedAt()).isNotNull());
    }

    /**
     * The vector travels with the prediction, as an object. Without it a prediction cannot be
     * explained a week later once the inputs have moved — and turning it into the text a column
     * holds is the adapter's business, not this one's.
     */
    @Test
    void predictClassGroup_handsTheVectorItScoredToTheWriteSide() {
        givenMarks(completeMarks(ana, mathGroup), mathGroup);
        when(modelClient.predictBatch(anyList()))
                .thenReturn(
                        List.of(
                                new RiskScore(
                                        RiskLevel.SIN_RIESGO,
                                        new BigDecimal("0.02"),
                                        new BigDecimal("0.10"))));
        when(predictionDomain.upsertAll(anyList()))
                .thenReturn(
                        List.of(
                                new UpsertResult(
                                        stored(ana, mathGroup, RiskLevel.SIN_RIESGO),
                                        RiskLevel.SIN_RIESGO)));

        service.predictClassGroup(mathGroup, TRIMESTER);

        assertThat(captureWrite().get(0).features())
                .satisfies(
                        vector -> {
                            assertThat(vector.studentId()).isEqualTo(ana);
                            assertThat(vector.knowing())
                                    .singleElement()
                                    .satisfies(mark -> assertThat(mark).isEqualByComparingTo("30"));
                            assertThat(vector.plannedCriteria()).isEqualTo(8);
                        });
    }

    /**
     * Position is the only thing tying an answer to its student, so an answer of a different length
     * cannot be read as a partial result. It reaches the caller as "the model said something this
     * side cannot read" — a 503 about another service, not a 500 about this one.
     */
    @Test
    void predictClassGroup_theModelAnswersTheWrongNumberOfScores_writesNothing() {
        List<CriterionScoreRow> marks = new ArrayList<>(completeMarks(ana, mathGroup));
        marks.addAll(completeMarks(bruno, mathGroup));
        givenMarks(marks, mathGroup);
        when(modelClient.predictBatch(anyList()))
                .thenReturn(
                        List.of(
                                new RiskScore(
                                        RiskLevel.SIN_RIESGO,
                                        new BigDecimal("0.02"),
                                        new BigDecimal("0.10"))));

        assertThatThrownBy(() -> service.predictClassGroup(mathGroup, TRIMESTER))
                .isInstanceOf(RiskModelUnavailableException.class)
                .hasMessageContaining("1 scores for 2 vectors");

        verify(predictionDomain, never()).upsertAll(anyList());
    }

    /**
     * Nothing planned is nothing to divide progress by, so nobody is predicted — but the students
     * are still counted. Left out of both halves of the summary, a subject whose teacher entered
     * marks without planning criteria reports as though it did not exist, and the run says it
     * looked at nothing when it looked at a class.
     */
    @Test
    void predictClassGroup_noCriteriaPlanned_countsTheStudentsItCouldNotPredict() {
        when(featureDomain.criterionScores(any(), anyInt()))
                .thenReturn(completeMarks(ana, mathGroup));
        when(featureDomain.plannedCriteriaCount(any(), anyInt())).thenReturn(Map.of());
        when(featureDomain.attendanceRates(any(), anyInt())).thenReturn(List.of());

        RunSummary summary = service.predictClassGroup(mathGroup, TRIMESTER);

        assertThat(summary.considered()).isEqualTo(1);
        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.predicted()).isZero();
        verifyNoInteractions(modelClient);
    }

    // ---------------------------------------------------------------- who gets told

    /**
     * The bound automation made necessary. The sweep runs within minutes of every save, so a
     * student whose level oscillates while their marks are entered crosses into a demanding
     * category several times an afternoon — every crossing a real transition, and every one a
     * message. A teacher told four times about the same child stops reading the bell.
     */
    @Test
    void predictClassGroup_aStudentAlreadyAnnouncedToday_isNotAnnouncedAgain() {
        givenMarks(completeMarks(ana, mathGroup), mathGroup);
        when(modelClient.predictBatch(anyList()))
                .thenReturn(
                        List.of(
                                new RiskScore(
                                        RiskLevel.RIESGO_CRITICO,
                                        new BigDecimal("0.81"),
                                        new BigDecimal("0.00"))));
        RiskPrediction row = stored(ana, mathGroup, RiskLevel.RIESGO_CRITICO);
        when(predictionDomain.upsertAll(anyList()))
                .thenReturn(List.of(new UpsertResult(row, RiskLevel.EN_RIESGO)));
        // Nothing left to claim: this prediction already spoke today.
        when(predictionDomain.claimForNotification(anyCollection(), any(), any()))
                .thenReturn(Set.of());

        RunSummary summary = service.predictClassGroup(mathGroup, TRIMESTER);

        // The run is unchanged and still reports the transition — only the teacher's inbox is
        // spared. The prediction itself is written either way.
        assertThat(summary.changed()).isEqualTo(1);
        verifyNoInteractions(notifications);
    }

    /**
     * The claim is asked for once, with today's start, and only about the demanding transitions.
     */
    @Test
    void predictClassGroup_asksToClaimOnlyTheTransitionsItWouldAnnounce() {
        givenMarks(completeMarks(ana, mathGroup), mathGroup);
        when(modelClient.predictBatch(anyList()))
                .thenReturn(
                        List.of(
                                new RiskScore(
                                        RiskLevel.SIN_RIESGO,
                                        new BigDecimal("0.10"),
                                        new BigDecimal("0.00"))));
        RiskPrediction row = stored(ana, mathGroup, RiskLevel.SIN_RIESGO);
        when(predictionDomain.upsertAll(anyList()))
                .thenReturn(List.of(new UpsertResult(row, RiskLevel.RIESGO_CRITICO)));

        service.predictClassGroup(mathGroup, TRIMESTER);

        // A student who left the failing band is a transition, and not one anybody is written to
        // about — so nothing is claimed, and no row is stamped as having spoken today.
        verify(predictionDomain, never()).claimForNotification(anyCollection(), any(), any());
        verifyNoInteractions(notifications);
    }

    @Test
    void predictClassGroup_aStudentEnteringTheFailingBand_tellsTheirTeacherByName() {
        givenMarks(completeMarks(ana, mathGroup), mathGroup);
        when(modelClient.predictBatch(anyList()))
                .thenReturn(
                        List.of(
                                new RiskScore(
                                        RiskLevel.RIESGO_CRITICO,
                                        new BigDecimal("0.81"),
                                        new BigDecimal("0.00"))));
        RiskPrediction row = stored(ana, mathGroup, RiskLevel.RIESGO_CRITICO);
        when(predictionDomain.upsertAll(anyList()))
                .thenReturn(List.of(new UpsertResult(row, RiskLevel.EN_RIESGO)));
        givenAnnouncementLookups(
                classGroup(mathGroup, teacher),
                new StudentRisk(row, "Ana", "Alvarez", "Matematicas"));

        RunSummary summary = service.predictClassGroup(mathGroup, TRIMESTER);

        assertThat(summary.changed()).isEqualTo(1);
        ArgumentCaptor<SendNotificationCommand> captor = ArgumentCaptor.captor();
        verify(notifications).send(captor.capture());
        SendNotificationCommand sent = captor.getValue();
        assertThat(sent.receiverId()).isEqualTo(teacher);
        assertThat(sent.message()).contains("Alvarez Ana").contains("Matematicas");
        assertThat(sent.resourceId()).isEqualTo(mathGroup);
    }

    /** Neutral Spanish in anything a teacher reads, not the Rioplatense the office speaks. */
    @Test
    void predictClassGroup_theWarningIsWrittenInNeutralSpanish() {
        givenMarks(completeMarks(ana, mathGroup), mathGroup);
        when(modelClient.predictBatch(anyList()))
                .thenReturn(
                        List.of(
                                new RiskScore(
                                        RiskLevel.RIESGO_CRITICO,
                                        new BigDecimal("0.81"),
                                        new BigDecimal("0.00"))));
        RiskPrediction row = stored(ana, mathGroup, RiskLevel.RIESGO_CRITICO);
        when(predictionDomain.upsertAll(anyList()))
                .thenReturn(List.of(new UpsertResult(row, RiskLevel.EN_RIESGO)));
        givenAnnouncementLookups(
                classGroup(mathGroup, teacher),
                new StudentRisk(row, "Ana", "Alvarez", "Matematicas"));

        service.predictClassGroup(mathGroup, TRIMESTER);

        ArgumentCaptor<SendNotificationCommand> captor = ArgumentCaptor.captor();
        verify(notifications).send(captor.capture());
        assertThat(captor.getValue().message()).contains("Revise").doesNotContain("Revisá");
    }

    /**
     * The probability moves on every run; the category does not. Announcing a student who is where
     * they already were fills the inbox nobody then opens on the day a real one arrives.
     */
    @Test
    void predictClassGroup_aStudentStillFailing_isNotAnnouncedAgain() {
        givenMarks(completeMarks(ana, mathGroup), mathGroup);
        when(modelClient.predictBatch(anyList()))
                .thenReturn(
                        List.of(
                                new RiskScore(
                                        RiskLevel.RIESGO_CRITICO,
                                        new BigDecimal("0.90"),
                                        new BigDecimal("0.00"))));
        when(predictionDomain.upsertAll(anyList()))
                .thenReturn(
                        List.of(
                                new UpsertResult(
                                        stored(ana, mathGroup, RiskLevel.RIESGO_CRITICO),
                                        RiskLevel.RIESGO_CRITICO)));

        RunSummary summary = service.predictClassGroup(mathGroup, TRIMESTER);

        assertThat(summary.changed()).isZero();
        verifyNoInteractions(notifications);
    }

    /** Most of the school sits in {@code EnRiesgo}. Announcing it announces everybody. */
    @Test
    void predictClassGroup_aStudentMerelyScrapingAPass_isNotAnnounced() {
        givenMarks(completeMarks(ana, mathGroup), mathGroup);
        when(modelClient.predictBatch(anyList()))
                .thenReturn(
                        List.of(
                                new RiskScore(
                                        RiskLevel.EN_RIESGO,
                                        new BigDecimal("0.30"),
                                        new BigDecimal("0.02"))));
        when(predictionDomain.upsertAll(anyList()))
                .thenReturn(
                        List.of(
                                new UpsertResult(
                                        stored(ana, mathGroup, RiskLevel.EN_RIESGO),
                                        RiskLevel.SIN_RIESGO)));

        RunSummary summary = service.predictClassGroup(mathGroup, TRIMESTER);

        assertThat(summary.changed()).isEqualTo(1);
        verifyNoInteractions(notifications);
    }

    /**
     * A student whose very first prediction is already the failing one has not moved anywhere, and
     * nobody has been told about them either. Staying silent would mean the students who most need
     * attention are exactly the ones never announced.
     */
    @Test
    void predictClassGroup_aFirstPredictionThatIsAlreadyFailing_isAnnounced() {
        givenMarks(completeMarks(ana, mathGroup), mathGroup);
        when(modelClient.predictBatch(anyList()))
                .thenReturn(
                        List.of(
                                new RiskScore(
                                        RiskLevel.RIESGO_CRITICO,
                                        new BigDecimal("0.88"),
                                        new BigDecimal("0.00"))));
        RiskPrediction row = stored(ana, mathGroup, RiskLevel.RIESGO_CRITICO);
        when(predictionDomain.upsertAll(anyList()))
                .thenReturn(List.of(new UpsertResult(row, null)));
        givenAnnouncementLookups(
                classGroup(mathGroup, teacher),
                new StudentRisk(row, "Ana", "Alvarez", "Matematicas"));

        service.predictClassGroup(mathGroup, TRIMESTER);

        verify(notifications).send(any(SendNotificationCommand.class));
    }

    /** Six students lost to the failing band is one thing to read about the course, not six. */
    @Test
    void predictClassGroup_severalStudentsAtOnce_isOneMessagePerSubject() {
        List<CriterionScoreRow> marks = new ArrayList<>(completeMarks(ana, mathGroup));
        marks.addAll(completeMarks(bruno, mathGroup));
        givenMarks(marks, mathGroup);
        when(modelClient.predictBatch(anyList()))
                .thenReturn(
                        List.of(
                                new RiskScore(
                                        RiskLevel.RIESGO_CRITICO,
                                        new BigDecimal("0.81"),
                                        new BigDecimal("0.00")),
                                new RiskScore(
                                        RiskLevel.RIESGO_CRITICO,
                                        new BigDecimal("0.77"),
                                        new BigDecimal("0.00"))));
        RiskPrediction anaRow = stored(ana, mathGroup, RiskLevel.RIESGO_CRITICO);
        RiskPrediction brunoRow = stored(bruno, mathGroup, RiskLevel.RIESGO_CRITICO);
        when(predictionDomain.upsertAll(anyList()))
                .thenReturn(
                        List.of(
                                new UpsertResult(anaRow, RiskLevel.EN_RIESGO),
                                new UpsertResult(brunoRow, RiskLevel.SIN_RIESGO)));
        givenAnnouncementLookups(
                classGroup(mathGroup, teacher),
                new StudentRisk(anaRow, "Ana", "Alvarez", "Matematicas"),
                new StudentRisk(brunoRow, "Bruno", "Bermudez", "Matematicas"));

        service.predictClassGroup(mathGroup, TRIMESTER);

        ArgumentCaptor<SendNotificationCommand> captor = ArgumentCaptor.captor();
        verify(notifications).send(captor.capture());
        assertThat(captor.getValue().message()).contains("Alvarez Ana").contains("Bermudez Bruno");
    }

    /**
     * Two reads for the whole run, however many subjects it touched. Resolving the teacher and the
     * names one subject at a time is how a sweep of the school becomes hundreds of round trips.
     */
    @Test
    void predictYear_manySubjectsAtOnce_resolvesTheirTeachersAndNamesInOneReadEach() {
        when(featureDomain.activeClassGroupIds(YEAR)).thenReturn(List.of(mathGroup, languageGroup));
        List<CriterionScoreRow> marks = new ArrayList<>(completeMarks(ana, mathGroup));
        marks.addAll(completeMarks(bruno, languageGroup));
        givenMarks(marks, mathGroup, languageGroup);
        when(modelClient.predictBatch(anyList()))
                .thenReturn(
                        List.of(
                                new RiskScore(
                                        RiskLevel.RIESGO_CRITICO,
                                        new BigDecimal("0.81"),
                                        new BigDecimal("0.00")),
                                new RiskScore(
                                        RiskLevel.RIESGO_CRITICO,
                                        new BigDecimal("0.77"),
                                        new BigDecimal("0.00"))));
        RiskPrediction anaRow = stored(ana, mathGroup, RiskLevel.RIESGO_CRITICO);
        RiskPrediction brunoRow = stored(bruno, languageGroup, RiskLevel.RIESGO_CRITICO);
        when(predictionDomain.upsertAll(anyList()))
                .thenReturn(
                        List.of(
                                new UpsertResult(anaRow, RiskLevel.EN_RIESGO),
                                new UpsertResult(brunoRow, RiskLevel.EN_RIESGO)));
        when(classGroupDomain.findByIdIn(anyCollection()))
                .thenReturn(
                        List.of(
                                classGroup(mathGroup, teacher),
                                classGroup(languageGroup, teacher)));
        when(predictionDomain.byIds(anyCollection()))
                .thenReturn(
                        List.of(
                                new StudentRisk(anaRow, "Ana", "Alvarez", "Matematicas"),
                                new StudentRisk(brunoRow, "Bruno", "Bermudez", "Lenguaje")));
        grantTheDailyClaim();

        service.predictYear(YEAR, TRIMESTER);

        verify(classGroupDomain, times(1)).findByIdIn(anyCollection());
        verify(predictionDomain, times(1)).byIds(anyCollection());
        verify(classGroupDomain, never()).findById(any());
        verify(predictionDomain, never()).byClassGroupAndTrimester(any(), anyInt());
        verify(notifications, times(2)).send(any(SendNotificationCommand.class));
    }

    /**
     * A subject nobody teaches yet still gets its prediction written; there is simply no inbox to
     * put the warning in. Reaching for a null receiver would fail the whole run over a message.
     */
    @Test
    void predictClassGroup_aSubjectWithNoTeacher_stillWritesThePredictionAndSendsNothing() {
        givenMarks(completeMarks(ana, languageGroup), languageGroup);
        when(modelClient.predictBatch(anyList()))
                .thenReturn(
                        List.of(
                                new RiskScore(
                                        RiskLevel.RIESGO_CRITICO,
                                        new BigDecimal("0.81"),
                                        new BigDecimal("0.00"))));
        RiskPrediction row = stored(ana, languageGroup, RiskLevel.RIESGO_CRITICO);
        when(predictionDomain.upsertAll(anyList()))
                .thenReturn(List.of(new UpsertResult(row, RiskLevel.SIN_RIESGO)));
        givenAnnouncementLookups(
                classGroup(languageGroup, null),
                new StudentRisk(row, "Ana", "Alvarez", "Lenguaje"));

        RunSummary summary = service.predictClassGroup(languageGroup, TRIMESTER);

        assertThat(summary.predicted()).isEqualTo(1);
        verify(predictionDomain).upsertAll(anyList());
        verifyNoInteractions(notifications);
    }

    // ---------------------------------------------------------------- reads

    /**
     * The names come through, because a panel handed bare uuids has to resolve every one of them.
     */
    @Test
    void byClassGroup_handsBackTheNamesTheRepositoryAlreadySelected() {
        RiskPrediction row = stored(ana, mathGroup, RiskLevel.RIESGO_CRITICO);
        when(predictionDomain.byClassGroupAndTrimester(mathGroup, TRIMESTER))
                .thenReturn(List.of(new StudentRisk(row, "Ana", "Alvarez", "Matematicas")));

        List<StudentRisk> risks = service.byClassGroup(mathGroup, TRIMESTER);

        assertThat(risks)
                .singleElement()
                .satisfies(
                        risk -> {
                            assertThat(risk.studentFullName()).isEqualTo("Alvarez Ana");
                            assertThat(risk.subjectName()).isEqualTo("Matematicas");
                        });
    }

    @Test
    void markAttended_recordsThatSomebodyActedOnIt() {
        RiskPrediction row = stored(ana, mathGroup, RiskLevel.RIESGO_CRITICO);
        when(predictionDomain.markAttended(eq(row.id()), eq(true))).thenReturn(row);

        assertThat(service.markAttended(row.id(), true)).isEqualTo(row);
    }

    private List<NewRiskPrediction> captureWrite() {
        ArgumentCaptor<List<NewRiskPrediction>> captor = ArgumentCaptor.captor();
        verify(predictionDomain).upsertAll(captor.capture());
        return captor.getValue();
    }
}
