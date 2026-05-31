package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.pdc;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.Pdc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface IPdcDomain {
    Pdc save(Pdc pdc);
    Optional<Pdc> findById(UUID id);
    boolean classGroupExists(UUID classGroupId);
    boolean existsByClassGroupAndTrimester(UUID classGroupId, Integer trimester);
    UUID teacherIdOfClassGroup(UUID classGroupId);
    Page<Pdc> list(UUID classGroupId, Integer trimester, String status, Pageable pageable);
    void deleteById(UUID id);
}
