package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.institution.Institution;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.institution.IInstitutionDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.UserEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class InstitutionRepositoryAdapter implements IInstitutionDomain {

    private static final String DIRECTOR_ROLE = "Director";

    private final JpaUserRepository userRepo;
    private final String district;
    private final String school;
    private final String department;
    private final String dependency;
    private final String shift;
    private final String educationLevel;

    public InstitutionRepositoryAdapter(
        JpaUserRepository userRepo,
        @Value("${app.institution.district}") String district,
        @Value("${app.institution.school}") String school,
        @Value("${app.institution.department}") String department,
        @Value("${app.institution.dependency}") String dependency,
        @Value("${app.institution.shift}") String shift,
        @Value("${app.institution.education-level}") String educationLevel) {
        this.userRepo = userRepo;
        this.district = district;
        this.school = school;
        this.department = department;
        this.dependency = dependency;
        this.shift = shift;
        this.educationLevel = educationLevel;
    }

    @Override
    public Institution current() {
        // A school has one Director, but nothing in the schema enforces it and a handover can leave
        // two rows for a day. The first by surname is taken rather than failing: a document that
        // cannot be printed is worse than one naming either of two people in office.
        String directorName = userRepo
            .findFirstByRole_NameAndActiveTrueOrderByLastNames(DIRECTOR_ROLE)
            .map(InstitutionRepositoryAdapter::fullName)
            .orElse(null);
        return new Institution(district, school, directorName,
            department, dependency, shift, educationLevel);
    }

    private static String fullName(UserEntity u) {
        return u.getNames() + " " + u.getLastNames();
    }
}
