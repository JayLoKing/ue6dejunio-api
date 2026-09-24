package bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Opens a month's plan for a course. The subject blocks come later through {@link
 * UpsertPdcSubjectCommand}: a plan is written subject by subject over days, not filed whole in one
 * request.
 *
 * @param classGroupIds which class groups of the course the plan covers. Empty means every active
 *     one, which is what a homeroom teacher wants; a specialist names the single group they teach.
 */
public record CreatePdcCommand(
        UUID courseId,
        Integer planNumber,
        Integer trimester,
        LocalDate periodStart,
        LocalDate periodEnd,
        String holisticObjective,
        String finalProduct,
        String bibliography,
        List<UUID> classGroupIds) {}
