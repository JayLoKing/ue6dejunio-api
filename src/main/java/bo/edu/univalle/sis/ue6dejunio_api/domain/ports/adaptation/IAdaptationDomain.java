package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.adaptation;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.Adaptation;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.CreateAdaptationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.UpdateAdaptationCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface IAdaptationDomain {
    boolean planExists(UUID planId);
    boolean studentExists(UUID studentId);
    boolean existsByPlanAndStudent(UUID planId, UUID studentId);
    Adaptation create(CreateAdaptationCommand command);
    Adaptation update(UUID id, UpdateAdaptationCommand command);
    Optional<Adaptation> findById(UUID id);
    Page<Adaptation> listByPlan(UUID planId, Pageable pageable);
    void deleteById(UUID id);
}
