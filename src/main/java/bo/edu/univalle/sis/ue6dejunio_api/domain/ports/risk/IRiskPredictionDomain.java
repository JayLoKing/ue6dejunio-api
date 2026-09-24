package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.NewRiskPrediction;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskLevel;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskPrediction;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.StudentRisk;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface IRiskPredictionDomain {

    /**
     * Writes a run's worth of predictions, correcting the standing row for each (student, subject,
     * trimester) rather than appending beside it.
     *
     * <p>Batched, and reports what each row held before. The table is an upsert target, so the
     * previous level is gone the instant the new one lands — and the previous level is the only
     * thing that can answer "did this student get worse", which is the one question worth writing
     * to a teacher about. Read here, inside the write, because reading it separately is a race: two
     * runs overlapping would both see the same "before" and both announce the same change.
     *
     * <p>Reporting rather than deciding: this returns the fact (what was there, what is there now)
     * and leaves what counts as news to the service.
     *
     * @return one result per prediction, in the same order as the input
     */
    List<UpsertResult> upsertAll(List<NewRiskPrediction> predictions);

    /**
     * Named predictions by id, in one query.
     *
     * <p>What the announcement side needs after a run: it holds the handful of rows whose category
     * moved, spread across whichever subjects they landed in, and it needs the students named. One
     * read for all of them rather than one per subject, which over a sweep of the school is the
     * difference between a query and a hundred.
     */
    List<StudentRisk> byIds(Collection<UUID> predictionIds);

    /**
     * Of these predictions, the ones that may be announced now — and marks them announced.
     *
     * <p>Both halves in one call on purpose. Asking "which of these were already announced today"
     * and then separately recording that they have been leaves a window where two runs both get the
     * same answer and both write to the same teacher, which is the duplicate this exists to stop.
     *
     * <p>Why it exists at all: the announcement fires on a transition into a category that demands
     * attention, and since the sweep runs within minutes of every save, a student whose level
     * oscillates while their teacher enters marks crosses that line several times an afternoon.
     * Each crossing is a real transition, and each one was a message. A teacher told four times
     * about the same child stops reading any of it.
     *
     * <p>The grain is the prediction row, which is already unique on student, subject and trimester
     * — so it is exactly "once a day per student per subject" without a second key.
     *
     * @param notBefore predictions announced at or after this instant are left out. The start of
     *     today, for a once-a-day bound.
     * @return the ids the caller may announce. Everything else has already been said today.
     */
    Set<UUID> claimForNotification(
            Collection<UUID> predictionIds, LocalDateTime now, LocalDateTime notBefore);

    /** Everyone predicted in one subject this trimester, worst first. */
    List<StudentRisk> byClassGroupAndTrimester(UUID classGroupId, int trimester);

    /** Everyone predicted in any subject of a course this trimester, worst first. */
    List<StudentRisk> byCourseAndTrimester(UUID courseId, int trimester);

    /** Every standing prediction for one student, worst first. */
    List<StudentRisk> byStudent(UUID studentId);

    Optional<RiskPrediction> findById(UUID id);

    /** Records that somebody acted on this prediction. The only field here a person writes. */
    RiskPrediction markAttended(UUID id, boolean attended);

    /**
     * What one write did.
     *
     * @param previousLevel null when this student and subject had never been predicted before
     */
    record UpsertResult(RiskPrediction stored, RiskLevel previousLevel) {

        /**
         * Whether the discrete category moved. The probability moves on every run; this does not.
         */
        public boolean levelChanged() {
            return previousLevel == null || previousLevel != stored.riskLevel();
        }
    }
}
