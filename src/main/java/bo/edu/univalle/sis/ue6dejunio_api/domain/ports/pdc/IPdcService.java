package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.pdc;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.CreatePdcCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.Pdc;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.UpdatePdcCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface IPdcService {
    Pdc create(CreatePdcCommand command, UUID currentUserId);
    Pdc update(UUID id, UpdatePdcCommand command, UUID currentUserId);
    Pdc getById(UUID id);
    Page<Pdc> list(UUID classGroupId, Integer trimester, String status, Pageable pageable);
    Pdc publish(UUID id, UUID currentUserId);
    Pdc approve(UUID id);
    Pdc observe(UUID id, String observations);
    void delete(UUID id, UUID currentUserId);
}
