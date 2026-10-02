package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearLockDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaAcademicYearRepository;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class AcademicYearLockRepositoryAdapter implements IAcademicYearLockDomain {

    private final JpaAcademicYearRepository yearRepo;

    public AcademicYearLockRepositoryAdapter(JpaAcademicYearRepository yearRepo) {
        this.yearRepo = yearRepo;
    }

    @Override
    public Set<Integer> closedYearsOfClassGroups(Collection<UUID> classGroupIds) {
        return Set.copyOf(yearRepo.closedYearsOfClassGroups(classGroupIds));
    }

    @Override
    public Set<Integer> closedYearsOfEnrollments(Collection<UUID> courseEnrollmentIds) {
        return Set.copyOf(yearRepo.closedYearsOfEnrollments(courseEnrollmentIds));
    }

    @Override
    public Set<Integer> closedYearsOfCourses(Collection<UUID> courseIds) {
        return Set.copyOf(yearRepo.closedYearsOfCourses(courseIds));
    }

    @Override
    public Set<Integer> closedYearsOfPlans(Collection<UUID> planIds) {
        return Set.copyOf(yearRepo.closedYearsOfPlans(planIds));
    }

    @Override
    public Set<Integer> closedYearsOfRiskPredictions(Collection<UUID> predictionIds) {
        return Set.copyOf(yearRepo.closedYearsOfRiskPredictions(predictionIds));
    }
}
