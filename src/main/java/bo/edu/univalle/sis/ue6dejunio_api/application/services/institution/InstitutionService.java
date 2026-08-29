package bo.edu.univalle.sis.ue6dejunio_api.application.services.institution;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.institution.Institution;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.institution.IInstitutionDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.institution.IInstitutionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InstitutionService implements IInstitutionService {

    private final IInstitutionDomain institutionDomain;

    public InstitutionService(IInstitutionDomain institutionDomain) {
        this.institutionDomain = institutionDomain;
    }

    @Override
    public Institution current() {
        return institutionDomain.current();
    }
}
