package bo.edu.univalle.sis.ue6dejunio_api.application.services.progress;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.PdcStatus;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.progress.CreateProgressCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.progress.PlanProgress;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.progress.IProgressDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.progress.IProgressService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgressService implements IProgressService {

    private final IProgressDomain progressDomain;
    private final ClosedYearGuard closedYearGuard;

    public ProgressService(IProgressDomain progressDomain, ClosedYearGuard closedYearGuard) {
        this.progressDomain = progressDomain;
        this.closedYearGuard = closedYearGuard;
    }

    /**
     * Records progress on a plan. Progress measures the delivery of a plan the director already
     * approved, so any other status is a business conflict (409), checked after the closed-year
     * guard so a closed gestión keeps its own message.
     */
    @Override
    @Transactional
    public PlanProgress create(CreateProgressCommand c) {
        String status =
                progressDomain
                        .findPlanStatus(c.planId())
                        .orElseThrow(() -> new ResourceNotFoundException("PDC", c.planId()));
        closedYearGuard.requireOpenForPlan(c.planId());
        if (!PdcStatus.APPROVED.equals(status)) {
            throw new ConflictException("Solo se puede registrar avance de un PDC aprobado.");
        }
        return progressDomain.create(
                c.planId(),
                c.progressDate(),
                c.advancedContent(),
                c.percentage(),
                c.observations(),
                c.createdBy());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanProgress> listByPlan(UUID planId) {
        return progressDomain.listByPlan(planId);
    }
}
