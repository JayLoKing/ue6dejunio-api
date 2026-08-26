package bo.edu.univalle.sis.ue6dejunio_api.application.services.adaptation;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.Adaptation;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.CreateAdaptationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.UpdateAdaptationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.adaptation.IAdaptationDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.adaptation.IAdaptationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AdaptationService implements IAdaptationService {

    private final IAdaptationDomain adaptationDomain;

    public AdaptationService(IAdaptationDomain adaptationDomain) {
        this.adaptationDomain = adaptationDomain;
    }

    @Override
    @Transactional
    public Adaptation create(CreateAdaptationCommand c) {
        if (!adaptationDomain.planExists(c.planId())) {
            throw new ResourceNotFoundException("PDC", c.planId());
        }
        if (!adaptationDomain.studentExists(c.studentId())) {
            throw new ResourceNotFoundException("Estudiante", c.studentId());
        }
        if (adaptationDomain.existsByPlanAndStudent(c.planId(), c.studentId())) {
            throw new DuplicateResourceException("adaptacion (plan + estudiante)",
                c.planId() + "/" + c.studentId());
        }
        return adaptationDomain.create(c);
    }

    @Override
    @Transactional
    public Adaptation update(UUID id, UpdateAdaptationCommand c) {
        getById(id);
        return adaptationDomain.update(id, c);
    }

    @Override
    @Transactional(readOnly = true)
    public Adaptation getById(UUID id) {
        return adaptationDomain.findById(id)
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
        getById(id);
        adaptationDomain.deleteById(id);
    }
}
