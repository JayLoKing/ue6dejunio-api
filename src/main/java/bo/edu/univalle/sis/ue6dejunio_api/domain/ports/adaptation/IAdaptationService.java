package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.adaptation;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.Adaptation;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.CreateAdaptationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.UpdateAdaptationCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface IAdaptationService {
    Adaptation create(CreateAdaptationCommand command);
    Adaptation update(UUID id, UpdateAdaptationCommand command);
    Adaptation getById(UUID id);
    Page<Adaptation> listByPlan(UUID planId, Pageable pageable);
    void delete(UUID id);
}
