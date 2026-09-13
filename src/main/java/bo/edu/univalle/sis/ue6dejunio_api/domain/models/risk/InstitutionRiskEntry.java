package bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One place on the school-wide risk list: which student, out of which classroom, and the subject
 * that put them there.
 *
 * <p>One row per student, which is what separates this from the panel a teacher opens. A course
 * panel is read by somebody who teaches those subjects and wants every one of them; a school-wide
 * list read at subject grain can spend its ten places on one child, and the nine others it pushed
 * off are exactly the ones the Director opened it to find.
 *
 * <p>The subject that travels with the entry is the student's worst, because that is the one
 * whoever reads this has to act on. The classroom travels too: a name with no classroom beside it
 * names nobody in a building with two students called the same.
 *
 * @param position     the place on the list, starting at one. Worst first.
 * @param predictionId the prediction this row stands for. Attending one is per prediction and not
 *                     per screen, so a row without it cannot be acted on.
 * @param pFail        never null: a prediction the model never scored cannot be placed against ones
 *                     it did, so it holds no place at all.
 */
public record InstitutionRiskEntry(
    int position,
    UUID predictionId,
    UUID studentId,
    String fullName,
    UUID courseId,
    String gradeName,
    String parallelName,
    UUID classGroupId,
    String subjectName,
    RiskLevel riskLevel,
    BigDecimal pFail,
    boolean attended
) {}
