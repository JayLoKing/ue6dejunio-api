package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AcademicYearEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaAcademicYearRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class AcademicYearRepositoryAdapter implements IAcademicYearDomain {

    private final JpaAcademicYearRepository yearRepo;

    public AcademicYearRepositoryAdapter(JpaAcademicYearRepository yearRepo) {
        this.yearRepo = yearRepo;
    }

    @Override
    @Transactional
    public Integer ensureYear(int year) {
        return yearRepo.findByYear(year)
                .map(AcademicYearEntity::getId)
                .orElseGet(() -> insertOrRecoverExisting(year));
    }

    /**
     * Single-instance startup provisioning; a concurrent-insert race is a non-issue in practice,
     * but UNIQUE(year) is still respected defensively by falling back to the existing row instead
     * of propagating the constraint violation.
     */
    private Integer insertOrRecoverExisting(int year) {
        AcademicYearEntity entity = new AcademicYearEntity();
        entity.setYear(year);
        try {
            return yearRepo.save(entity).getId();
        } catch (DataIntegrityViolationException e) {
            return yearRepo.findByYear(year).map(AcademicYearEntity::getId).orElseThrow(() -> e);
        }
    }

    @Override
    public Integer currentYearId() {
        return yearRepo.findTopByOrderByYearDesc()
                .map(AcademicYearEntity::getId)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", "actual"));
    }
}
