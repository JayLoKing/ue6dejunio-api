package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.InstitutionRiskEntry;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One place on the school-wide risk list.
 *
 * <p>Deliberately not {@link StudentRiskResponse}. That one is a prediction and its two names, one
 * row per subject; this one is a student, their classroom and the single subject that put them on
 * the list. Sharing a payload between the two would hand every screen a shape whose meaning changes
 * with the scope it asked for.
 *
 * @param predictionId what the attend button acts on. Attending is per prediction, not per screen.
 * @param riskLevel    the model's spelling, {@code RiesgoCritico} and not the enum constant — the
 *                     same vocabulary the per-subject panel already reads.
 */
public record InstitutionRiskEntryResponse(
    int position,
    UUID predictionId,
    UUID studentId,
    String fullName,
    UUID courseId,
    String gradeName,
    String parallelName,
    UUID classGroupId,
    String subjectName,
    String riskLevel,
    BigDecimal pFail,
    boolean attended
) {
    public static InstitutionRiskEntryResponse from(InstitutionRiskEntry e) {
        return new InstitutionRiskEntryResponse(e.position(), e.predictionId(), e.studentId(),
            e.fullName(), e.courseId(), e.gradeName(), e.parallelName(), e.classGroupId(),
            e.subjectName(), e.riskLevel().modelName(), e.pFail(), e.attended());
    }
}
