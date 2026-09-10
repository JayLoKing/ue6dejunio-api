package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskPredictionService.RunSummary;

/**
 * What a run did, so the caller can tell "nothing to predict" from "nothing happened".
 *
 * @param considered students the run looked at
 * @param skipped    students without a mark in at least one of the four dimensions. The normal case
 *                   early in a trimester, and not a failure.
 * @param predicted  vectors actually sent to the model
 * @param changed    predictions whose category moved, which is what anyone was told about
 */
public record RiskRunResponse(int considered, int skipped, int predicted, int changed) {

    public static RiskRunResponse from(RunSummary summary) {
        return new RiskRunResponse(summary.considered(), summary.skipped(),
            summary.predicted(), summary.changed());
    }
}
