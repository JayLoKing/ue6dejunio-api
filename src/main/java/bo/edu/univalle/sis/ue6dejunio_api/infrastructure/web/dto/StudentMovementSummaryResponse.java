package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentMovementSummary;
import java.util.List;

/**
 * Secretaría's movement table.
 *
 * <p>{@code byMonth} carries only the months that saw movement, so a gestión that started in
 * February has no January row rather than a row of zeros — the chart draws the gaps it is given,
 * and an invented zero is a month the school reports as quiet when it had not begun.
 */
public record StudentMovementSummaryResponse(
        List<MonthlyMovementResponse> byMonth, List<WithdrawalReasonResponse> byReason) {

    public record MonthlyMovementResponse(int year, int month, int enrolled, int withdrawn) {}

    /** {@code reason} is nullable: a withdrawal recorded before V14 added the column has none. */
    public record WithdrawalReasonResponse(String reason, int students) {}

    public static StudentMovementSummaryResponse from(StudentMovementSummary s) {
        return new StudentMovementSummaryResponse(
                s.byMonth().stream()
                        .map(
                                m ->
                                        new MonthlyMovementResponse(
                                                m.year(), m.month(), m.enrolled(), m.withdrawn()))
                        .toList(),
                s.byReason().stream()
                        .map(r -> new WithdrawalReasonResponse(r.reason(), r.students()))
                        .toList());
    }
}
