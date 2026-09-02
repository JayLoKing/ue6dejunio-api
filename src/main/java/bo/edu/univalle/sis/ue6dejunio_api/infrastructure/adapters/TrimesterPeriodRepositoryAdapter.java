package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.TrimesterPeriod;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.trimesterperiod.ITrimesterPeriodDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AcademicYearEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.TrimesterPeriodEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.mappers.TrimesterPeriodMapper;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaAcademicYearRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaTrimesterPeriodRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class TrimesterPeriodRepositoryAdapter implements ITrimesterPeriodDomain {

    private final JpaTrimesterPeriodRepository trimesterPeriodRepo;
    private final JpaAcademicYearRepository academicYearRepo;
    private final TrimesterPeriodMapper mapper;

    public TrimesterPeriodRepositoryAdapter(JpaTrimesterPeriodRepository trimesterPeriodRepo,
                                            JpaAcademicYearRepository academicYearRepo,
                                            TrimesterPeriodMapper mapper) {
        this.trimesterPeriodRepo = trimesterPeriodRepo;
        this.academicYearRepo = academicYearRepo;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TrimesterPeriod save(Integer academicYearId, int trimester, LocalDate startDate, LocalDate endDate) {
        AcademicYearEntity year = academicYearRepo.findById(academicYearId)
            .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", academicYearId));
        TrimesterPeriodEntity e = new TrimesterPeriodEntity();
        e.setAcademicYear(year);
        e.setTrimester(trimester);
        e.setStartDate(startDate);
        e.setEndDate(endDate);
        return mapper.toDomain(trimesterPeriodRepo.save(e));
    }

    @Override
    @Transactional
    public TrimesterPeriod update(UUID id, LocalDate startDate, LocalDate endDate) {
        TrimesterPeriodEntity e = trimesterPeriodRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("TrimesterPeriod", id));
        e.setStartDate(startDate);
        e.setEndDate(endDate);
        return mapper.toDomain(trimesterPeriodRepo.save(e));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        trimesterPeriodRepo.deleteById(id);
    }

    @Override
    public Optional<TrimesterPeriod> findById(UUID id) {
        return trimesterPeriodRepo.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<TrimesterPeriod> findByAcademicYear(Integer academicYearId) {
        return trimesterPeriodRepo.findByAcademicYear_IdOrderByTrimester(academicYearId).stream()
            .map(mapper::toDomain).toList();
    }

    @Override
    public boolean academicYearExists(Integer academicYearId) {
        return academicYearRepo.existsById(academicYearId);
    }

    @Override
    public boolean existsByAcademicYearAndTrimester(Integer academicYearId, int trimester) {
        return trimesterPeriodRepo.existsByAcademicYear_IdAndTrimester(academicYearId, trimester);
    }

}
