package bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.AnnualResult;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * What closing a year freezes on one enrollment.
 *
 * <p>Both values are null for an enrollment with nothing graded: no data is not a failure.
 *
 * @param courseEnrollmentId the enrollment the result belongs to.
 * @param finalAverage the annual average the libreta prints, two decimals; null when ungraded.
 * @param result the result decided on the printed mark; null exactly when {@code finalAverage} is.
 */
public record FrozenResult(UUID courseEnrollmentId, BigDecimal finalAverage, AnnualResult result) {}
