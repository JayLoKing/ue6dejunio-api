package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.CourseRiskSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.InstitutionRiskEntry;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskPrediction;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.StudentRisk;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface IRiskPredictionService {

    /**
     * Predicts every subject of every active class group in the year.
     *
     * <p>The whole school at once, on demand. Not what the scheduled sweep runs — that spends the
     * queue through {@link #predictClassGroups}, and only on the subjects something changed in.
     */
    RunSummary predictYear(int academicYear, int trimester);

    /**
     * Predicts one subject, for when a teacher wants an answer now rather than at the next sweep.
     */
    RunSummary predictClassGroup(UUID classGroupId, int trimester);

    /**
     * Predicts a set of subjects that share a trimester. What the queue sweep spends its marks on.
     *
     * <p>A collection and not a loop over {@link #predictClassGroup}, because the features of any
     * number of class groups are read in the same four queries and sent in the same batch. Called
     * once per subject, a drain of twenty classrooms would be twenty round trips to a model that
     * would have taken them in one.
     *
     * @param classGroupIds the subjects to predict. Empty is a no-op, not an error: a sweep that
     *     found nothing has nothing to ask.
     */
    RunSummary predictClassGroups(Collection<UUID> classGroupIds, int trimester);

    /**
     * The standing predictions of one subject, worst first, each with the names that make it
     * readable.
     *
     * <p>{@link StudentRisk} rather than the bare prediction: a prediction is two uuids, and every
     * screen that shows one needs the student and the subject named. Handing back the bare record
     * pushes that lookup onto each caller, and the panel that lists thirty students becomes thirty
     * extra queries — after the repository had already selected the names in the same statement.
     */
    List<StudentRisk> byClassGroup(UUID classGroupId, int trimester);

    /**
     * The standing predictions of every subject of a course, worst first, narrowed to the reader.
     *
     * <p>The course's homeroom teacher reads all of its subjects on purpose; any other teacher
     * reads only the subjects they run there. The caller (who knows the role) says which reader
     * this is, so the rule lives here and not in a controller.
     *
     * @param teacherScopeId {@code null} for a reader with no ownership scope (Director,
     *     secretariat): every row. Otherwise the teacher asking: the whole course if they are its
     *     homeroom teacher, only the rows of the class groups they teach in it if they are not.
     */
    List<StudentRisk> byCourse(UUID courseId, int trimester, UUID teacherScopeId);

    /**
     * One row per classroom of the gestión, counting students by their worst risk band.
     *
     * <p>Students and not predictions: the model files one row per subject, so a child failing
     * three areas would otherwise be counted three times and a classroom would report more students
     * at risk than it holds. The ones the sweep has not reached are counted apart, so an unswept
     * classroom is not mistaken for a safe one.
     */
    List<CourseRiskSummary> courseRiskSummaries(Integer academicYearId, int trimester);

    /**
     * The students of the whole school closest to failing this trimester, worst first.
     *
     * <p>One row per student, unlike every listing above it. Those are read by somebody who teaches
     * the subjects and wants each one; this one is a fixed number of places, and spent at subject
     * grain they can all go to a single child while the others it displaced are the ones the reader
     * opened it to find. The subject that comes with each student is their worst.
     *
     * <p>Only students in {@code RiesgoCritico} are listed, and the subject shown is the worst of
     * their critical ones. A trimester with fewer critical students than {@code places} answers a
     * shorter list rather than padding it with students who are passing.
     *
     * @param academicYearId the gestión, required. Without it the course listing answers every year
     *     at once, and the list would rank a student of 2024 beside one of 2026.
     * @param places how many students the list holds. Taken from every course first and then once
     *     more from the merge, which is exact rather than an approximation: a student who is
     *     eleventh in their own classroom has ten worse ahead of them and cannot be in the school's
     *     worst ten.
     */
    List<InstitutionRiskEntry> institutionRisk(Integer academicYearId, int trimester, int places);

    /**
     * One student across every subject they sit.
     *
     * <p>Same reading rule as {@link #byCourse}, applied per course the student is enrolled in: a
     * teacher reads every subject of a course they are homeroom of and only the subjects they run
     * in any other.
     *
     * @param teacherScopeId {@code null} for a reader with no ownership scope (Director,
     *     secretariat): every row. Otherwise the teacher asking.
     */
    List<StudentRisk> byStudent(UUID studentId, UUID teacherScopeId);

    RiskPrediction markAttended(UUID predictionId, boolean attended);

    /**
     * What a run did, so the caller can tell "nothing to predict" from "nothing happened".
     *
     * @param considered students the run looked at
     * @param skipped students missing a mark in at least one of the four dimensions. The normal
     *     case early in a trimester, and not a failure.
     * @param predicted vectors actually sent to the model
     * @param changed predictions whose category moved, which is what anyone was told about
     */
    record RunSummary(int considered, int skipped, int predicted, int changed) {

        public static RunSummary empty() {
            return new RunSummary(0, 0, 0, 0);
        }
    }
}
