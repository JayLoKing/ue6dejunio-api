package bo.edu.univalle.sis.ue6dejunio_api.application.services.progress;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.progress.CreateProgressCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.progress.PlanProgress;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.progress.IProgressDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.progress.IProgressService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProgressService implements IProgressService {

    private final IProgressDomain progressDomain;

    public ProgressService(IProgressDomain progressDomain) {
        this.progressDomain = progressDomain;
    }

    @Override
    @Transactional
    public PlanProgress create(CreateProgressCommand c) {
        if (!progressDomain.planExists(c.planId())) {
            throw new ResourceNotFoundException("PDC", c.planId());
        }
        return progressDomain.create(c.planId(), c.progressDate(), c.advancedContent(),
            c.percentage(), c.observations(), c.createdBy());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanProgress> listByPlan(UUID planId) {
        return progressDomain.listByPlan(planId);
    }
}
