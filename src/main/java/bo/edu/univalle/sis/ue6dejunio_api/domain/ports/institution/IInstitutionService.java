package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.institution;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.institution.Institution;

public interface IInstitutionService {

    /** The heading of any document the school hands in. */
    Institution current();
}
