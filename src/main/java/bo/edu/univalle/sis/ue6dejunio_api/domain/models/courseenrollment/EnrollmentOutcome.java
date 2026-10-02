package bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.AnnualResult;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * What an enrollment holds from the last time its year was closed, together with whether the year
 * is closed now.
 *
 * <p>The two travel together because the stored values outlive a reopen: a reopened year keeps the
 * average and result of its last close until the next one overwrites them, so they mean something
 * only while {@code yearClosed} is true.
 *
 * @param courseEnrollmentId the enrollment the values belong to.
 * @param yearClosed whether the enrollment's academic year is closed right now.
 * @param finalAverage the frozen annual average; null for a withdrawn or ungraded enrollment.
 * @param result the frozen result; null exactly when {@code finalAverage} is.
 */
public record EnrollmentOutcome(
        UUID courseEnrollmentId,
        boolean yearClosed,
        BigDecimal finalAverage,
        AnnualResult result) {}
