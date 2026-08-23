package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.DailyBatchResult;

/**
 * Outcome of a whole-group attendance write: how many marks arrived and how many rows were
 * actually persisted, which differ when the same enrollment is sent twice.
 */
public record BatchResultResponse(int total, int saved) {
    public static BatchResultResponse from(DailyBatchResult result) {
        return new BatchResultResponse(result.total(), result.saved());
    }
}
