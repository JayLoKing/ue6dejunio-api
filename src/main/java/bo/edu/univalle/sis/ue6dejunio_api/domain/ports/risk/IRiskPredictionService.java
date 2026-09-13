package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.InstitutionRiskEntry;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskPrediction;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.StudentRisk;

import java.util.List;
import java.util.UUID;

public interface IRiskPredictionService {

    /** Predicts every subject of every active class group in the year. What the sweep runs. */
    RunSummary predictYear(int academicYear, int trimester);

    /** Predicts one subject, for when a teacher wants an answer now rather than at the next sweep. */
    RunSummary predictClassGroup(UUID classGroupId, int trimester);

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

    List<StudentRisk> byCourse(UUID courseId, int trimester);

    /**
     * The students of the whole school closest to failing this trimester, worst first.
     *
     * <p>One row per student, unlike every listing above it. Those are read by somebody who teaches
     * the subjects and wants each one; this one is a fixed number of places, and spent at subject
     * grain they can all go to a single child while the others it displaced are the ones the reader
     * opened it to find. The subject that comes with each student is their worst.
     *
     * @param academicYearId the gestión, required. Without it the course listing answers every year
     *                       at once, and the list would rank a student of 2024 beside one of 2026.
     * @param places         how many students the list holds. Taken from every course first and then
     *                       once more from the merge, which is exact rather than an approximation: a
     *                       student who is eleventh in their own classroom has ten worse ahead of
     *                       them and cannot be in the school's worst ten.
     */
    List<InstitutionRiskEntry> institutionRisk(Integer academicYearId, int trimester, int places);

    List<StudentRisk> byStudent(UUID studentId);

    RiskPrediction markAttended(UUID predictionId, boolean attended);

    /**
     * What a run did, so the caller can tell "nothing to predict" from "nothing happened".
     *
     * @param considered students the run looked at
     * @param skipped    students missing a mark in at least one of the four dimensions. The normal
     *                   case early in a trimester, and not a failure.
     * @param predicted  vectors actually sent to the model
     * @param changed    predictions whose category moved, which is what anyone was told about
     */
    record RunSummary(int considered, int skipped, int predicted, int changed) {

        public static RunSummary empty() {
            return new RunSummary(0, 0, 0, 0);
        }
    }
}
