package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.adaptation;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.Adaptation;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.CreateAdaptationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.UpdateAdaptationCommand;

import java.util.Optional;
import java.util.UUID;

public interface IAdaptationDomain {
    boolean planExists(UUID planId);
    boolean studentExists(UUID studentId);
    boolean existsByPlanAndStudent(UUID planId, UUID studentId);
    Adaptation create(CreateAdaptationCommand command);
    Adaptation update(UUID id, UpdateAdaptationCommand command);
    Optional<Adaptation> findById(UUID id);
    PageResult<Adaptation> listByPlan(UUID planId, PageQuery pageQuery);
    void deleteById(UUID id);
}
