package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.adaptation;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.Adaptation;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.CreateAdaptationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.UpdateAdaptationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import java.util.UUID;

public interface IAdaptationService {
    Adaptation create(CreateAdaptationCommand command);

    Adaptation update(UUID id, UpdateAdaptationCommand command);

    Adaptation getById(UUID id);

    PageResult<Adaptation> listByPlan(UUID planId, PageQuery pageQuery);

    void delete(UUID id);
}
