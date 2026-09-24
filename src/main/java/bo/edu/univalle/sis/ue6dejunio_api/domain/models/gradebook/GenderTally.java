package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

import java.math.BigDecimal;

/**
 * One column of section III: how many boys, how many girls, how many in all, and what share of the
 * effective roster that is — the sheet's {@code V | M | T | %}.
 *
 * <p><b>{@code male + female} need not equal {@code total}.</b> {@code students.gender} is
 * nullable, so a student enrolled before anyone recorded theirs is counted in the total and in
 * neither of the two columns. Splitting them by guess would put a child under a heading the school
 * never wrote down, and dropping them from the total would make the report disagree with the roster
 * the same screen shows.
 *
 * @param percentage share of the effective roster, two decimals, or null when there is no effective
 *     roster to be a share of. A zero there would read as "nobody passed" on a course that has no
 *     students at all.
 */
public record GenderTally(int male, int female, int total, BigDecimal percentage) {}
