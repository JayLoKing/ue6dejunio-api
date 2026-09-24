package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentEvent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.CreateEventCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.UpdateEventCommand;
import java.util.List;
import java.util.UUID;

public interface IAssessmentEventService {
    AssessmentEvent create(CreateEventCommand command);

    AssessmentEvent update(UUID id, UpdateEventCommand command);

    AssessmentEvent getById(UUID id);

    List<AssessmentEvent> listByCriterion(UUID criterionId);

    void delete(UUID id);
}
