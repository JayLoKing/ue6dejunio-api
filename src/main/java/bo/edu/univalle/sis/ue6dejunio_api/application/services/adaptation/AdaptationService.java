package bo.edu.univalle.sis.ue6dejunio_api.application.services.adaptation;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.Adaptation;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.CreateAdaptationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.UpdateAdaptationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.adaptation.IAdaptationDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.adaptation.IAdaptationService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdaptationService implements IAdaptationService {

    private final IAdaptationDomain adaptationDomain;
    private final ClosedYearGuard closedYearGuard;

    public AdaptationService(IAdaptationDomain adaptationDomain, ClosedYearGuard closedYearGuard) {
        this.adaptationDomain = adaptationDomain;
        this.closedYearGuard = closedYearGuard;
    }

    @Override
    @Transactional
    public Adaptation create(CreateAdaptationCommand c) {
        if (!adaptationDomain.planExists(c.planId())) {
            throw new ResourceNotFoundException("PDC", c.planId());
        }
        closedYearGuard.requireOpenForPlan(c.planId());
        if (!adaptationDomain.studentExists(c.studentId())) {
            throw new ResourceNotFoundException("Estudiante", c.studentId());
        }
        if (adaptationDomain.existsByPlanAndStudent(c.planId(), c.studentId())) {
            throw new DuplicateResourceException(
                    "adaptacion (plan + estudiante)", c.planId() + "/" + c.studentId());
        }
        return adaptationDomain.create(c);
    }

    @Override
    @Transactional
    public Adaptation update(UUID id, UpdateAdaptationCommand c) {
        closedYearGuard.requireOpenForPlan(getById(id).planId());
        return adaptationDomain.update(id, c);
    }

    @Override
    @Transactional(readOnly = true)
    public Adaptation getById(UUID id) {
        return adaptationDomain
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Adaptacion", id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Adaptation> listByPlan(UUID planId, PageQuery pageQuery) {
        return adaptationDomain.listByPlan(planId, pageQuery);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        closedYearGuard.requireOpenForPlan(getById(id).planId());
        adaptationDomain.deleteById(id);
    }
}
