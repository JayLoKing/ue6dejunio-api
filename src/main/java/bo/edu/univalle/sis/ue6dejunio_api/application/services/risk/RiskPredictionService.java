package bo.edu.univalle.sis.ue6dejunio_api.application.services.risk;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.RiskModelUnavailableException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.NotificationType;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.SendNotificationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.InstitutionRiskEntry;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.NewRiskPrediction;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskAssessed;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskFeatures;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskPrediction;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.StudentRisk;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskFeatureDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskModelClient;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskPredictionDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskPredictionDomain.UpsertResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskPredictionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Runs the model over the school and keeps what it said.
 *
 * <p>Deliberately not transactional across a run. The model lives in another process, and a sweep
 * of the school is one HTTP call carrying hundreds of vectors — holding a database connection open
 * across it would tie the pool to a service this side does not control. Each write is its own
 * transaction, inside {@code IRiskPredictionDomain.upsertAll}, which is also the only place that
 * needs one.
 */
@Service
public class RiskPredictionService implements IRiskPredictionService {

    private static final Logger log = LoggerFactory.getLogger(RiskPredictionService.class);

    private final IRiskFeatureDomain featureDomain;
    private final IRiskModelClient modelClient;
    private final IRiskPredictionDomain predictionDomain;
    private final INotificationService notifications;
    private final IClassGroupDomain classGroupDomain;
    private final ICourseService courseService;

    public RiskPredictionService(
        IRiskFeatureDomain featureDomain,
        IRiskModelClient modelClient,
        IRiskPredictionDomain predictionDomain,
        INotificationService notifications,
        IClassGroupDomain classGroupDomain,
        ICourseService courseService
    ) {
        this.featureDomain = featureDomain;
        this.modelClient = modelClient;
        this.predictionDomain = predictionDomain;
        this.notifications = notifications;
        this.classGroupDomain = classGroupDomain;
        this.courseService = courseService;
    }

    @Override
    public RunSummary predictYear(int academicYear, int trimester) {
        return runFor(featureDomain.activeClassGroupIds(academicYear), trimester);
    }

    @Override
    public RunSummary predictClassGroup(UUID classGroupId, int trimester) {
        return predictClassGroups(List.of(classGroupId), trimester);
    }

    @Override
    public RunSummary predictClassGroups(Collection<UUID> classGroupIds, int trimester) {
        return runFor(classGroupIds, trimester);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentRisk> byClassGroup(UUID classGroupId, int trimester) {
        return predictionDomain.byClassGroupAndTrimester(classGroupId, trimester);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentRisk> byCourse(UUID courseId, int trimester) {
        return predictionDomain.byCourseAndTrimester(courseId, trimester);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InstitutionRiskEntry> institutionRisk(Integer academicYearId, int trimester, int places) {
        if (academicYearId == null) {
            throw new ValidationException("A school-wide risk list needs the gestión it belongs to");
        }
        /*
         * The worst of each course first, then the worst of those. Taking `places` from a course
         * before merging is exact and not an approximation: a student who is eleventh in their own
         * classroom already has ten worse children ahead of them there, so they cannot be in the
         * school's worst ten on that row. It is also what keeps this bounded — the whole school
         * never lands in memory at once, only a few rows per course.
         *
         * The collapse to one row per student then runs twice, and the second pass is not
         * redundant. `course_enrollments` is unique on student and course, not on student and
         * gestión: a child moved between parallels mid-year keeps an enrolment in both, and the
         * predictions filed against their old classroom do not disappear with the move.
         */
        List<InstitutionRiskEntry> worst = new ArrayList<>();
        for (Course course : courseService.allOfYear(academicYearId)) {
            worst.addAll(worstOf(course, trimester, places));
        }
        return ranked(collapsedByStudent(worst), places);
    }

    /** One row per student, keeping the worst of whatever rows they hold. */
    private static List<InstitutionRiskEntry> collapsedByStudent(List<InstitutionRiskEntry> entries) {
        Map<UUID, InstitutionRiskEntry> byStudent = new LinkedHashMap<>();
        for (InstitutionRiskEntry entry : entries) {
            byStudent.merge(entry.studentId(), entry, WORST_SUBJECT);
        }
        return List.copyOf(byStudent.values());
    }

    /** One course's worst students, one row each, read from every subject of the course. */
    private List<InstitutionRiskEntry> worstOf(Course course, int trimester, int places) {
        List<InstitutionRiskEntry> rows = new ArrayList<>();
        for (StudentRisk risk : predictionDomain.byCourseAndTrimester(course.id(), trimester)) {
            // A prediction the model never scored cannot be placed against ones it did. Sorting it
            // as a zero would say the model looked at the child and found them safe.
            if (risk.prediction().pFail() == null) {
                continue;
            }
            rows.add(entryOf(course, risk));
        }
        return ranked(collapsedByStudent(rows), places);
    }

    /**
     * Which of two of the same student's subjects the row keeps: the worse one.
     *
     * <p>The tie is broken on the subject's own name rather than left to whichever the query
     * returned first. Two subjects can sit on the same probability, and kept by arrival order the
     * row would name Lenguaje on one reading and Matematicas on the next off the same unchanged
     * predictions — the same reason the list itself breaks its ties on something the reader can see.
     */
    private static final BinaryOperator<InstitutionRiskEntry> WORST_SUBJECT = (kept, candidate) -> {
        int byProbability = candidate.pFail().compareTo(kept.pFail());
        if (byProbability != 0) {
            return byProbability > 0 ? candidate : kept;
        }
        return candidate.subjectName().compareTo(kept.subjectName()) < 0 ? candidate : kept;
    };

    private static InstitutionRiskEntry entryOf(Course course, StudentRisk risk) {
        RiskPrediction prediction = risk.prediction();
        return new InstitutionRiskEntry(0, prediction.id(), prediction.studentId(),
            risk.studentFullName(), course.id(), course.gradeName(), course.parallelName(),
            prediction.classGroupId(), risk.subjectName(), prediction.riskLevel(),
            prediction.pFail(), prediction.attended());
    }

    /**
     * The worst {@code places} of what it is given, numbered from one.
     *
     * <p>The tie is broken on the name the list itself shows, so two students on the same
     * probability come out in the same order on two readings and what decides it is something the
     * reader can see.
     */
    private static List<InstitutionRiskEntry> ranked(List<InstitutionRiskEntry> entries, int places) {
        List<InstitutionRiskEntry> sorted = entries.stream()
            .sorted(Comparator.comparing(InstitutionRiskEntry::pFail).reversed()
                .thenComparing(InstitutionRiskEntry::fullName))
            .limit(places)
            .toList();
        return IntStream.range(0, sorted.size())
            .mapToObj(i -> {
                InstitutionRiskEntry e = sorted.get(i);
                return new InstitutionRiskEntry(i + 1, e.predictionId(), e.studentId(), e.fullName(),
                    e.courseId(), e.gradeName(), e.parallelName(), e.classGroupId(), e.subjectName(),
                    e.riskLevel(), e.pFail(), e.attended());
            })
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentRisk> byStudent(UUID studentId) {
        return predictionDomain.byStudent(studentId);
    }

    @Override
    @Transactional
    public RiskPrediction markAttended(UUID predictionId, boolean attended) {
        return predictionDomain.markAttended(predictionId, attended);
    }

    private RunSummary runFor(Collection<UUID> classGroupIds, int trimester) {
        if (classGroupIds.isEmpty()) {
            return RunSummary.empty();
        }

        List<RiskFeatures> candidates = RiskFeatureAssembler.assemble(
            trimester,
            featureDomain.criterionScores(classGroupIds, trimester),
            featureDomain.plannedCriteriaCount(classGroupIds, trimester),
            featureDomain.attendanceRates(classGroupIds, trimester)
        );

        List<RiskFeatures> complete = candidates.stream().filter(RiskFeatures::isComplete).toList();
        int considered = candidates.size();
        int skipped = considered - complete.size();

        if (complete.isEmpty()) {
            return new RunSummary(considered, skipped, 0, 0);
        }

        List<RiskScore> scores = modelClient.predictBatch(complete);
        List<UpsertResult> written = predictionDomain.upsertAll(toPredictions(complete, scores));

        List<RiskAssessed> transitions = written.stream()
            .filter(UpsertResult::levelChanged)
            .map(RiskPredictionService::assessmentOf)
            .toList();

        announce(transitions, trimester);

        return new RunSummary(considered, skipped, complete.size(), transitions.size());
    }

    /**
     * The vectors and the model's answers, paired back up by position.
     *
     * <p>Position is the whole pairing — the model answers a list, not a map — so a mismatch in
     * length would silently attach one student's score to another's row. The client contracts to
     * return one score per vector in order and to fail rather than return a partial list, and this
     * checks it anyway, because the cost of being wrong here is a prediction filed under the wrong
     * child.
     */
    private List<NewRiskPrediction> toPredictions(List<RiskFeatures> vectors, List<RiskScore> scores) {
        if (scores.size() != vectors.size()) {
            // The model answered something this side cannot read, which is what this exception is
            // for and why it reaches the caller as a 503. An IllegalStateException here would tell
            // the Director his school's system broke and send him looking in the wrong place.
            throw new RiskModelUnavailableException(
                "The model answered " + scores.size() + " scores for " + vectors.size()
                    + " vectors, so no score can be tied to the student it belongs to.");
        }

        // One clock reading for the whole run: every row of a sweep is the same statement about the
        // same moment, and staggering them by microseconds would invite a reader to sort by it.
        LocalDateTime predictedAt = LocalDateTime.now();

        List<NewRiskPrediction> predictions = new ArrayList<>(vectors.size());
        for (int i = 0; i < vectors.size(); i++) {
            RiskFeatures vector = vectors.get(i);
            RiskScore score = scores.get(i);
            predictions.add(new NewRiskPrediction(
                vector.studentId(),
                vector.classGroupId(),
                vector.trimester(),
                score.level(),
                score.pFail(),
                score.pOutstanding(),
                vector,
                predictedAt));
        }
        return predictions;
    }

    private static RiskAssessed assessmentOf(UpsertResult result) {
        RiskPrediction stored = result.stored();
        return new RiskAssessed(
            stored.id(),
            stored.studentId(),
            stored.classGroupId(),
            stored.trimester(),
            result.previousLevel(),
            stored.riskLevel());
    }

    /**
     * Writes to the teachers whose students moved into the failing category.
     *
     * <p>Transitions, not states. The probability moves on every run — any mark entered anywhere in
     * the subject shifts it — and a message per run per student is an inbox nobody opens on the day
     * the real one arrives. Only the discrete category crossing into {@code RiesgoCritico} is news.
     *
     * <p>One message per subject rather than one per student. A teacher who lost six students to
     * the failing band this week has one thing to read about their course, not six.
     *
     * <p>Two queries for the whole run, not two per subject: a sweep of the school can touch every
     * class group in it, and resolving the teacher and the names one subject at a time inside the
     * loop is how a run turns into hundreds of round trips.
     */
    private void announce(List<RiskAssessed> transitions, int trimester) {
        List<RiskAssessed> critical = transitions.stream()
            .filter(transition -> transition.currentLevel().demandsAttention())
            .toList();
        if (critical.isEmpty()) {
            return;
        }

        Map<UUID, ClassGroup> subjects = classGroupDomain
            .findByIdIn(critical.stream().map(RiskAssessed::classGroupId).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(ClassGroup::id, Function.identity()));

        Map<UUID, String> names = predictionDomain
            .byIds(critical.stream().map(RiskAssessed::predictionId).toList())
            .stream()
            .collect(Collectors.toMap(
                risk -> risk.prediction().studentId(),
                StudentRisk::studentFullName,
                (first, second) -> first));

        Map<UUID, Set<UUID>> studentsBySubject = new LinkedHashMap<>();
        for (RiskAssessed transition : critical) {
            studentsBySubject
                .computeIfAbsent(transition.classGroupId(), id -> new LinkedHashSet<>())
                .add(transition.studentId());
        }

        studentsBySubject.forEach((classGroupId, studentIds) -> {
            ClassGroup subject = subjects.get(classGroupId);
            if (subject != null) {
                notifyTeacher(subject, studentIds, names, trimester);
            }
        });
    }

    private void notifyTeacher(ClassGroup classGroup, Set<UUID> studentIds,
                               Map<UUID, String> names, int trimester) {
        if (classGroup.teacherId() == null) {
            // A subject nobody teaches yet. The prediction is still written and the Director still
            // sees it; there is simply no inbox to put this in.
            log.info("No teacher assigned to class group {}, so {} risk transitions go unannounced",
                classGroup.id(), studentIds.size());
            return;
        }

        notifications.send(new SendNotificationCommand(
            null,
            classGroup.teacherId(),
            NotificationType.CUSTOM,
            "Riesgo académico en " + classGroup.subjectName(),
            namesOf(studentIds, names)
                + " en riesgo de reprobar " + classGroup.subjectName()
                + " (trimestre " + trimester + "). Revise el panel de riesgo.",
            "class_group",
            classGroup.id()));
    }

    /**
     * The students by name, because a warning about "un estudiante" is a warning the teacher has to
     * go and decode. Falls back to a count only if a name went missing between the write and the
     * read, which is a message worth sending anyway.
     */
    private static String namesOf(Set<UUID> studentIds, Map<UUID, String> names) {
        List<String> found = studentIds.stream().map(names::get).filter(Objects::nonNull).toList();
        return found.isEmpty() ? studentIds.size() + " estudiantes" : String.join(", ", found);
    }
}
