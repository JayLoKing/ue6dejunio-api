package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One area a student failed in the trimester, with the mark that failed it.
 *
 * <p>The school's document prints several of these inside a single cell of the student's row, one
 * per line. That is a rendering shape; here they stay a list, so whoever draws the sheet decides
 * how to stack them.
 *
 * @param mark the area's total for that trimester. Never null — an area with no mark was never
 *     judged and so cannot be among the ones a student failed.
 */
public record FailedArea(UUID classGroupId, String subjectName, BigDecimal mark) {}
