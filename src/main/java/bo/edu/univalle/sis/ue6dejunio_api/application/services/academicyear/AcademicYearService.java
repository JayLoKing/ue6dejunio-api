package bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.AcademicYearItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcademicYearService implements IAcademicYearService {

    private final IAcademicYearDomain academicYearDomain;

    public AcademicYearService(IAcademicYearDomain academicYearDomain) {
        this.academicYearDomain = academicYearDomain;
    }

    @Override
    @Transactional
    public AcademicYearItem activate(int academicYearId) {
        AcademicYearItem year =
                academicYearDomain
                        .findItem(academicYearId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "AcademicYear", academicYearId));
        if (year.closed()) {
            throw new ConflictException("La gestión está cerrada; reábrala antes de activarla.");
        }
        if (year.active()) {
            return year;
        }
        academicYearDomain.setActive(academicYearId);
        return new AcademicYearItem(year.id(), year.year(), true, false);
    }
}
