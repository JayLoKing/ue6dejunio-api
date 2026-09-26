package bo.edu.univalle.sis.ue6dejunio_api.domain.models.student;

import java.util.List;

/**
 * How the school's roll moved during a gestión: who came in, who left, and why.
 *
 * <p>WHAT THIS CAN AND CANNOT SAY, because the two halves do not have the same footing.
 *
 * <p>The intakes are solid. Every enrolment carries its own {@code enrollment_date}, so counting
 * them by month is counting rows that each recorded a day.
 *
 * <p>The withdrawals are the last word and not the whole story. A student's status lives in one row
 * on the student — {@code status}, {@code status_reason}, {@code status_changed_at} — and V14 said
 * so on purpose: "a history would need its own table; correcting a reason overwrites the previous
 * one, and that is accepted". So a child who withdrew in April and was re-admitted in June shows up
 * once, under whatever their status says today. For a school that loses a handful of students a
 * year this is the shape of the truth; for one that needed a ledger, the fix is that table and not
 * a cleverer query over this one.
 *
 * @param byMonth one row per month of the gestión that saw any movement at all
 * @param byReason how many students currently sit in each withdrawal reason
 */
public record StudentMovementSummary(
        List<MonthlyMovement> byMonth, List<WithdrawalReasonCount> byReason) {

    /**
     * @param year and
     * @param month the calendar month, 1-12
     * @param enrolled enrolments whose {@code enrollment_date} falls in it
     * @param withdrawn students whose status last changed to a withdrawal in it
     */
    public record MonthlyMovement(int year, int month, int enrolled, int withdrawn) {}

    /**
     * @param reason the category the school chose. Null when a withdrawal was recorded before V14
     *     added the column, which is why it is not assumed to be present
     * @param students how many currently carry it
     */
    public record WithdrawalReasonCount(String reason, int students) {}
}
