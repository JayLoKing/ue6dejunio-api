package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.pdc;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.Pdc;

import java.util.Optional;
import java.util.UUID;

public interface IPdcDomain {
    Pdc save(Pdc pdc);
    Optional<Pdc> findById(UUID id);
    boolean classGroupExists(UUID classGroupId);
    boolean existsByClassGroupAndTrimester(UUID classGroupId, Integer trimester);
    /**
     * @param teacherId narrows the listing to the plans of one teacher; {@code null} spans every
     *                  plan, which only the Director and the secretariat are entitled to.
     */
    PageResult<Pdc> list(UUID classGroupId, Integer trimester, String status, UUID teacherId,
                         PageQuery pageQuery);
    void deleteById(UUID id);
}
