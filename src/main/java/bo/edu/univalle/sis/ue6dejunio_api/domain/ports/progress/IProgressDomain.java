package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.progress;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.progress.PlanProgress;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IProgressDomain {
    /** The plan's status text (see {@code PdcStatus}), or empty when the plan does not exist. */
    Optional<String> findPlanStatus(UUID planId);

    PlanProgress create(
            UUID planId,
            LocalDate date,
            String content,
            BigDecimal pct,
            String obs,
            UUID createdBy);

    List<PlanProgress> listByPlan(UUID planId);
}
