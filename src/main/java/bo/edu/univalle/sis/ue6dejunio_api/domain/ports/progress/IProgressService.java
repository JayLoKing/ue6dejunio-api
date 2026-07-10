package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.progress;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.progress.CreateProgressCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.progress.PlanProgress;

import java.util.List;
import java.util.UUID;

public interface IProgressService {
    PlanProgress create(CreateProgressCommand command);
    List<PlanProgress> listByPlan(UUID planId);
}
