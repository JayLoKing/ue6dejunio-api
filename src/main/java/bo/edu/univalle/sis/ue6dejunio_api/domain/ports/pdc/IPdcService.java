package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.pdc;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.CreatePdcCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.Pdc;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.UpdatePdcCommand;

import java.util.UUID;

public interface IPdcService {
    Pdc create(CreatePdcCommand command, UUID currentUserId);
    Pdc update(UUID id, UpdatePdcCommand command, UUID currentUserId);
    Pdc getById(UUID id);
    PageResult<Pdc> list(UUID classGroupId, Integer trimester, String status, UUID teacherId,
                         PageQuery pageQuery);
    Pdc publish(UUID id, UUID currentUserId);
    Pdc approve(UUID id);
    Pdc observe(UUID id, String observations);
    void delete(UUID id);
}
