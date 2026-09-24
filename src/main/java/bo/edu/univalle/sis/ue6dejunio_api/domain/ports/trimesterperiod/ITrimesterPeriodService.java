package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.trimesterperiod;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.CreateTrimesterPeriodCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.TrimesterPeriod;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.UpdateTrimesterPeriodCommand;
import java.util.List;
import java.util.UUID;

public interface ITrimesterPeriodService {
    TrimesterPeriod create(CreateTrimesterPeriodCommand command);

    TrimesterPeriod update(UUID id, UpdateTrimesterPeriodCommand command);

    TrimesterPeriod getById(UUID id);

    List<TrimesterPeriod> listByAcademicYear(Integer academicYearId);

    void delete(UUID id);
}
